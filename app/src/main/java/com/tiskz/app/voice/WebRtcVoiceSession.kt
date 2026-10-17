package com.tiskz.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.SystemClock
import com.tiskz.app.analysis.AnalysisEvent
import com.tiskz.app.analysis.AnalysisSession
import com.tiskz.app.analysis.EventParser
import com.tiskz.app.analysis.UserDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Micrófono explícito, sin captura de llamadas de operadora ni grabaciones en disco. */
class WebRtcVoiceSession(
    private val context: Context,
    private val client: OkHttpClient,
    baseUrl: String,
    private var accessCode: String,
) : AnalysisSession() {
    enum class Connection { CONNECTING, STREAMING, ENDED }
    private val _connection = MutableStateFlow(Connection.CONNECTING)
    val connection = _connection.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val api = VoiceBackendApi(baseUrl, client)
    private val closed = AtomicBoolean(false)
    private val streaming = AtomicBoolean(false)
    private var started = false
    private var room: VoiceRoom? = null
    private var socket: WebSocket? = null
    private var peer: PeerConnection? = null
    private var factory: PeerConnectionFactory? = null
    private var audioModule: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var track: AudioTrack? = null
    private var channel: DataChannel? = null
    private val socketReady = CompletableDeferred<Unit>()
    private val iceReady = CompletableDeferred<Unit>()
    private val dataReady = CompletableDeferred<Unit>()
    private val microphoneReady = CompletableDeferred<Unit>()
    private val vad = VoiceActivityDetector()
    // Métricas sin contenido de audio para diagnosticar el micrófono virtual en pruebas.
    @Volatile internal var capturedPeakRms = 0.0
        private set
    @Volatile internal var committedSegments = 0
        private set
    private val relay = RealtimeTranscriptRelay(
        send = { event ->
            if (!closed.get() && socket?.send(event.toString()) != true) fail("Se perdió el canal de eventos.")
        },
        onError = { dispatch(AnalysisEvent.Error(it)) },
    )

    fun start(consent: Boolean) {
        if (started || closed.get()) return
        started = true
        scope.launch {
            try {
                check(consent) { "Confirma la autorización para analizar el audio." }
                check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    "Permite el acceso al micrófono para iniciar."
                }
                withTimeout(45000) {
                    room = api.createRoom(accessCode, consent)
                    accessCode = ""
                    openEvents(requireNotNull(room))
                    socketReady.await()
                    preparePeer()
                    val pc = requireNotNull(peer)
                    val offer = createOffer(pc)
                    setDescription(pc, offer, local = true)
                    if (pc.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) withTimeoutOrNull(3000) { iceReady.await() }
                    val answer = api.offer(requireNotNull(room), pc.localDescription.description)
                    setDescription(pc, SessionDescription(SessionDescription.Type.ANSWER, answer), local = false)
                    dataReady.await()
                    microphoneReady.await()
                    _connection.value = Connection.STREAMING
                }
            } catch (e: CancellationException) {
                if (!closed.get()) fail("La conexión de voz tardó demasiado. Vuelve a intentar.")
            } catch (e: Exception) {
                fail(if (e is java.io.IOException || e is IllegalArgumentException || e is IllegalStateException) e.message ?: "No se pudo iniciar la voz." else "No se pudo iniciar el canal de voz.")
            }
        }
    }

    private fun openEvents(room: VoiceRoom) {
        socket = client.newWebSocket(Request.Builder().url(api.eventsUrl(room)).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed.get()) { webSocket.close(1000, "ended"); return }
                webSocket.send(JSONObject().put("type", "session.start").toString())
                socketReady.complete(Unit)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (closed.get()) return
                EventParser.parse(text)?.let { event ->
                    dispatch(event)
                    if (event is AnalysisEvent.Ended) scope.launch { close() }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fail("Sin conexión con los agentes. El micrófono se ha detenido.")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.get()) fail("La sesión de voz terminó.")
            }
        })
    }

    private fun preparePeer() {
        initializeWebRtc(context)
        audioModule = JavaAudioDeviceModule.builder(context)
            .setSamplesReadyCallback { samples ->
                microphoneReady.complete(Unit)
                if (streaming.get() && samples.audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                    val rms = VoiceActivityDetector.pcm16Rms(samples.data)
                    capturedPeakRms = maxOf(capturedPeakRms, rms)
                    val commit = vad.accept(rms, SystemClock.elapsedRealtime())
                    if (commit) scope.launch { committedSegments++; sendData(JSONObject().put("type", "input_audio_buffer.commit")) }
                }
            }
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(message: String) = fail("No se pudo abrir el micrófono.")
                override fun onWebRtcAudioRecordStartError(code: JavaAudioDeviceModule.AudioRecordStartErrorCode, message: String) = fail("No se pudo iniciar el micrófono.")
                override fun onWebRtcAudioRecordError(message: String) = fail("El micrófono dejó de estar disponible.")
            }).createAudioDeviceModule()
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(audioModule).createPeerConnectionFactory()
        val configuration = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peer = requireNotNull(factory).createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) { if (state == PeerConnection.IceGatheringState.COMPLETE) iceReady.complete(Unit) }
            override fun onIceCandidate(candidate: IceCandidate) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                if (state == PeerConnection.PeerConnectionState.FAILED) fail("No se pudo mantener la conexión WebRTC.")
                if (state == PeerConnection.PeerConnectionState.DISCONNECTED) scope.launch {
                    delay(5000)
                    if (peer?.connectionState() == PeerConnection.PeerConnectionState.DISCONNECTED) fail("Se perdió la conexión de audio.")
                }
            }
        }) ?: error("No se pudo crear WebRTC.")
        audioSource = requireNotNull(factory).createAudioSource(MediaConstraints())
        track = requireNotNull(factory).createAudioTrack("tiskz-microphone", audioSource).also { it.setEnabled(false) }
        requireNotNull(peer).addTrack(track, listOf("tiskz-voice"))
        channel = requireNotNull(peer).createDataChannel("oai-events", DataChannel.Init())
        requireNotNull(channel).registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                scope.launch {
                    when (channel?.state()) {
                        DataChannel.State.OPEN -> {
                            streaming.set(true)
                            track?.setEnabled(true)
                            dataReady.complete(Unit)
                        }
                        DataChannel.State.CLOSED -> if (!closed.get()) fail("Se cerró la transcripción de voz.")
                        else -> Unit
                    }
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary || closed.get() || buffer.data.remaining() > 20000) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                scope.launch { relay.accept(bytes.toString(Charsets.UTF_8)) }
            }
        })
    }

    private fun sendData(event: JSONObject) {
        if (channel?.state() == DataChannel.State.OPEN) channel?.send(DataChannel.Buffer(ByteBuffer.wrap(event.toString().toByteArray(Charsets.UTF_8)), false))
    }

    override fun send(decision: UserDecision) {
        if (!closed.get()) socket?.send(JSONObject().put("type", "user.decision").put("action", decision.wire).toString())
    }

    private fun fail(message: String) {
        if (closed.get()) return
        scope.launch {
            if (!closed.get()) { dispatch(AnalysisEvent.Error(message)); close() }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        streaming.set(false)
        accessCode = ""
        track?.setEnabled(false)
        scope.cancel()
        api.cancelPending()
        socket?.close(1000, "voice ended")
        runCatching { channel?.unregisterObserver(); channel?.close(); channel?.dispose() }
        runCatching { peer?.close(); peer?.dispose() }
        runCatching { track?.dispose(); audioSource?.dispose(); factory?.dispose(); audioModule?.release() }
        channel = null; peer = null; track = null; audioSource = null; factory = null; audioModule = null; socket = null
        _connection.value = Connection.ENDED
        room?.let { current ->
            cleanupScope.launch { runCatching { withTimeout(6000) { api.end(current) } } }
        }
        room = null
    }

    private suspend fun createOffer(pc: PeerConnection): SessionDescription = suspendCancellableCoroutine { continuation ->
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) { if (continuation.isActive) continuation.resume(sdp) }
            override fun onCreateFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("No se pudo preparar el audio.")) }
        }, MediaConstraints())
    }

    private suspend fun setDescription(pc: PeerConnection, sdp: SessionDescription, local: Boolean): Unit = suspendCancellableCoroutine { continuation ->
        val observer = object : SimpleSdpObserver() {
            override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
            override fun onSetFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("No se pudo negociar el audio.")) }
        }
        if (local) pc.setLocalDescription(observer, sdp) else pc.setRemoteDescription(observer, sdp)
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }

    companion object {
        private var initialized = false
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Synchronized private fun initializeWebRtc(context: Context) {
            if (!initialized) {
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
                initialized = true
            }
        }
    }
}

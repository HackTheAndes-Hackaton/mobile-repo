package com.tiskz.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.SystemClock
import com.tiskz.app.analysis.AnalysisEvent
import com.tiskz.app.analysis.AnalysisSession
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
import okhttp3.WebSocket
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
    private val assistant: Boolean = false,
    private val evidenceConsent: Boolean = false,
) : AnalysisSession() {
    data class Evidence(val busy: Boolean = false, val saved: Boolean = false, val message: String = "")
    private val excerptBuffer = AudioExcerptBuffer()
    private val _evidence = MutableStateFlow(Evidence())
    val evidence = _evidence.asStateFlow()
    private var savedExcerptId: String? = null
    private var autoSaveAttempted = false
    private var pendingExcerpt: Pair<String, ByteArray>? = null
    @Volatile private var playingExcerpt = false
    private var excerptPlayer: android.media.AudioTrack? = null
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
    private var remoteTrack: AudioTrack? = null
    @Volatile internal var providerCloseConfirmed = false
        private set
    private val providerClosed = CompletableDeferred<Unit>()
    private val providerReady = CompletableDeferred<Unit>()
    @Volatile internal var assistantStarted = false
        private set
    private val socketReady = CompletableDeferred<Unit>()
    private val iceReady = CompletableDeferred<Unit>()
    private val dataReady = CompletableDeferred<Unit>()
    private val microphoneReady = CompletableDeferred<Unit>()
    private val vad = VoiceActivityDetector()
    // Métricas sin contenido de audio para diagnosticar el micrófono virtual en pruebas.
    @Volatile internal var capturedPeakRms = 0.0
        private set
    @Volatile internal var capturedSampleRate = 0
        private set
    @Volatile internal var capturedLastRms = 0.0
        private set
    @Volatile internal var capturedLastSampleAtMs = 0L
        private set
    @Volatile internal var capturedLastSpeechAtMs = 0L
        private set
    @Volatile internal var committedSegments = 0
        private set
    private val relay = RealtimeTranscriptRelay(
        send = { event ->
            if (!closed.get() && socket?.send(event.toString()) != true) fail("Se perdió el canal de eventos.")
        },
        onError = { dispatch(AnalysisEvent.Error(it)) },
    )

    private val liveRelay = LiveEventRelay(
        sendBackend = { event -> if (!closed.get() && socket?.send(event.toString()) != true) fail("Se perdió el canal de eventos.") },
        onStarted = { assistantStarted = true; providerReady.complete(Unit) },
        onClosed = { close() },
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
                    room = api.createRoom(accessCode, consent, assistant, evidenceConsent)
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
                    if (assistant) providerReady.await()
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
        socket = client.newWebSocket(Request.Builder().url(api.eventsUrl(room)).build(), VoiceEventsListener(
            isClosed = { closed.get() },
            onReady = { socketReady.complete(Unit) },
            onEvent = { event ->
                dispatch(event)
                if (evidenceConsent && state.value.alert == true && !autoSaveAttempted && excerptBuffer.snapshot() != null) {
                    autoSaveAttempted = true
                    saveExcerpt()
                }
                if (event is AnalysisEvent.Ended) scope.launch { close() }
            },
            onTerminated = ::fail,
            onControl = { raw -> if (assistant) LiveEventRelay.contextCommand(raw)?.let { command -> scope.launch { sendData(command) } } },
        ))
    }

    private fun preparePeer() {
        initializeWebRtc(context)
        audioModule = JavaAudioDeviceModule.builder(context)
            // Evita el valor de 8 kHz del emulador; conserva banda de voz para transcripción.
            .setInputSampleRate(48000)
            .setSamplesReadyCallback { samples ->
                if (evidenceConsent && streaming.get() && !playingExcerpt && samples.audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                    excerptBuffer.accept(samples.data, samples.sampleRate, samples.channelCount)
                }
                capturedSampleRate = samples.sampleRate
                microphoneReady.complete(Unit)
                if (streaming.get() && samples.audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                    val rms = VoiceActivityDetector.pcm16Rms(samples.data)
                    val now = SystemClock.elapsedRealtime()
                    capturedLastRms = rms
                    capturedLastSampleAtMs = now
                    if (rms > 0.012) capturedLastSpeechAtMs = now
                    capturedPeakRms = maxOf(capturedPeakRms, rms)
                    val commit = vad.accept(rms, now)
                    if (commit && !assistant) scope.launch { committedSegments++; sendData(JSONObject().put("type", "input_audio_buffer.commit")) }
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
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                if (assistant) (receiver.track() as? AudioTrack)?.let { audio ->
                    remoteTrack = audio; audio.setEnabled(true); audio.setVolume(1.0)
                }
            }
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
                if (buffer.binary || (closed.get() && !assistant) || buffer.data.remaining() > 20000) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val raw = bytes.toString(Charsets.UTF_8)
                val ended = assistant && runCatching { JSONObject(raw).optString("type") == "session.closed" }.getOrDefault(false)
                if (ended) {
                    providerCloseConfirmed = true
                    providerClosed.complete(Unit)
                    socket?.send(JSONObject().put("type", "live.event").put("event", JSONObject(raw)).toString())
                }
                if (!closed.get()) scope.launch { if (assistant) liveRelay.accept(raw) else relay.accept(raw) }
            }
        })
    }

    /** Energía recibida, sin guardar muestras de audio. Solo diagnóstico de integración. */
    internal suspend fun receivedAudioEnergy(): Double {
        val result = CompletableDeferred<Double>()
        val pc = peer ?: return 0.0
        pc.getStats { report ->
            result.complete(report.statsMap.values.filter { it.type == "inbound-rtp" }
                .sumOf { (it.members["totalAudioEnergy"] as? Number)?.toDouble() ?: 0.0 })
        }
        return withTimeoutOrNull(2000) { result.await() } ?: 0.0
    }

    private fun sendData(event: JSONObject) {
        if (channel?.state() == DataChannel.State.OPEN) channel?.send(DataChannel.Buffer(ByteBuffer.wrap(event.toString().toByteArray(Charsets.UTF_8)), false))
    }

    override fun send(decision: UserDecision) {
        if (!closed.get()) socket?.send(JSONObject().put("type", "user.decision").put("action", decision.wire).toString())
    }

    fun saveExcerpt() {
        if (!evidenceConsent || closed.get() || _evidence.value.busy) return
        val current = room ?: return
        val candidate = pendingExcerpt ?: excerptBuffer.snapshot()?.let { java.util.UUID.randomUUID().toString() to it }
        val audio = candidate?.second
        if (audio == null) { _evidence.value = _evidence.value.copy(message = "Habla al menos un segundo antes de guardar."); return }
        pendingExcerpt = candidate
        _evidence.value = _evidence.value.copy(busy = true, message = "Guardando fragmento autorizado…")
        scope.launch {
            try {
                savedExcerptId = api.saveExcerpt(current, audio, requireNotNull(candidate).first)
                pendingExcerpt = null
                audio.fill(0)
                _evidence.value = Evidence(saved = true, message = "Fragmento guardado · pendiente de revisión.")
            } catch (e: Exception) {
                if (!closed.get()) _evidence.value = Evidence(saved = savedExcerptId != null, message = "No se pudo guardar el audio. Puedes volver a intentar.")
            } finally { if (closed.get()) audio.fill(0) }
        }
    }

    fun playExcerpt() {
        val id = savedExcerptId ?: return
        val current = room ?: return
        if (closed.get() || _evidence.value.busy) return
        _evidence.value = _evidence.value.copy(busy = true, message = "Recuperando el fragmento del servidor…")
        scope.launch {
            var bytes: ByteArray? = null
            try {
                bytes = api.loadExcerpt(current, id)
                playingExcerpt = true
                track?.setEnabled(false)
                val player = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC).setBufferSizeInBytes(bytes.size - 44).build()
                excerptPlayer = player
                player.write(bytes, 44, bytes.size - 44)
                player.play()
                _evidence.value = _evidence.value.copy(message = "Reproduciendo · micrófono pausado.")
                delay(((bytes.size - 44) * 1000L / 32000) + 100)
            } catch (e: Exception) {
                if (!closed.get()) _evidence.value = _evidence.value.copy(message = "No se pudo reproducir el fragmento.")
            } finally {
                bytes?.fill(0)
                runCatching { excerptPlayer?.stop(); excerptPlayer?.release() }
                excerptPlayer = null; playingExcerpt = false
                if (!closed.get()) { track?.setEnabled(true); _evidence.value = _evidence.value.copy(busy = false) }
            }
        }
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
        excerptBuffer.clear()
        pendingExcerpt?.second?.fill(0); pendingExcerpt = null
        runCatching { excerptPlayer?.stop(); excerptPlayer?.release() }
        excerptPlayer = null
        accessCode = ""
        track?.setEnabled(false)
        remoteTrack?.setEnabled(false)
        // Retira el emisor para detener la captura; conserva recepción hasta confirmar el cierre.
        if (assistant) {
            peer?.senders?.forEach { sender -> if (sender.track()?.kind() == "audio") runCatching { peer?.removeTrack(sender) } }
            if (!providerClosed.isCompleted) sendData(JSONObject().put("type", "session.close"))
        }
        scope.cancel()
        api.cancelPending()
        val current = room
        room = null
        if (assistant) cleanupScope.launch(Dispatchers.Main.immediate) {
            withTimeoutOrNull(2000) { providerClosed.await() }
            releaseTransport()
            current?.let { cleanupScope.launch { runCatching { withTimeout(6000) { api.end(it) } } } }
        } else {
            releaseTransport()
            current?.let { cleanupScope.launch { runCatching { withTimeout(6000) { api.end(it) } } } }
        }
    }

    private fun releaseTransport() {
        socket?.close(1000, "voice ended")
        runCatching { channel?.unregisterObserver(); channel?.close(); channel?.dispose() }
        runCatching { peer?.close(); peer?.dispose() }
        runCatching { track?.dispose(); audioSource?.dispose(); factory?.dispose(); audioModule?.release() }
        remoteTrack = null; channel = null; peer = null; track = null; audioSource = null; factory = null; audioModule = null; socket = null
        _connection.value = Connection.ENDED
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

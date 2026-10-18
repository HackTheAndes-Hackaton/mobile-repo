package com.tiskz.app.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Audio de conversación: activa el procesamiento de voz y conserva auriculares. */
internal class VoiceAudioRoute(context: Context) : AutoCloseable {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var previousMode: Int? = null
    private var previousSpeaker = false
    private var previousDevice: AudioDeviceInfo? = null
    private var selectedDeviceId: Int? = null
    private var legacySpeakerChanged = false

    @Suppress("DEPRECATION")
    fun start() {
        if (previousMode != null) return
        check(manager.mode != AudioManager.MODE_IN_CALL) { "Termina la llamada de la operadora antes de iniciar el micrófono WebRTC." }
        previousMode = manager.mode
        previousSpeaker = manager.isSpeakerphoneOn
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) previousDevice = manager.communicationDevice
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // No desplaza un auricular o dispositivo de comunicación ya elegido.
            val current = manager.communicationDevice ?: previousDevice
            if (current == null || current.type in listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)) {
                manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let {
                    if (manager.setCommunicationDevice(it)) selectedDeviceId = it.id
                }
            }
        } else if (!manager.isBluetoothScoOn && !manager.isWiredHeadsetOn && !previousSpeaker) {
            manager.isSpeakerphoneOn = true
            legacySpeakerChanged = true
        }
    }

    @Suppress("DEPRECATION")
    override fun close() {
        val mode = previousMode ?: return
        previousMode = null
        // Si otra llamada tomó el audio, no cambia su ruta ni su modo.
        if (manager.mode != AudioManager.MODE_IN_COMMUNICATION) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && selectedDeviceId != null && manager.communicationDevice?.id == selectedDeviceId) {
            val previous = previousDevice
            if (previous != null && manager.availableCommunicationDevices.any { it.id == previous.id }) manager.setCommunicationDevice(previous)
            else manager.clearCommunicationDevice()
        } else if (legacySpeakerChanged && manager.isSpeakerphoneOn) manager.isSpeakerphoneOn = previousSpeaker
        if (mode != AudioManager.MODE_IN_COMMUNICATION) manager.mode = mode
    }
}

package com.example.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.example.model.AudioHardwareCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Inspects real Android output hardware via AudioManager + AudioDeviceCallback.
 * Honestly reports whether the connected device (e.g., USB audio interface) supports
 * 4+ discrete output channels, or only standard 2-channel stereo output.
 */
class AudioDeviceRouter(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _capability = MutableStateFlow(AudioHardwareCapability())
    val capability: StateFlow<AudioHardwareCapability> = _capability.asStateFlow()

    @Volatile
    var preferredDeviceInfo: AudioDeviceInfo? = null
        private set

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }
    }

    init {
        refreshDevices()
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    fun refreshDevices() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val priorityDevice = outputs.firstOrNull { isUsbDevice(it) }
            ?: outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: outputs.firstOrNull()

        preferredDeviceInfo = priorityDevice

        val maxCh = priorityDevice?.channelCounts?.maxOrNull()?.coerceAtLeast(2) ?: 2
        val isUsb = priorityDevice?.let { isUsbDevice(it) } ?: false
        val multiCh = maxCh >= 4

        val deviceSummaries = outputs.map { dev ->
            val ch = dev.channelCounts.maxOrNull() ?: 2
            val name = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: deviceTypeName(dev.type)
            "$name (${deviceTypeName(dev.type)}, ${ch}ch)"
        }.distinct()

        _capability.value = AudioHardwareCapability(
            activeDeviceName = priorityDevice?.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: deviceTypeName(priorityDevice?.type ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            activeDeviceType = deviceTypeName(priorityDevice?.type ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            maxOutputChannels = maxCh,
            supportsMultiChannelRouting = multiCh,
            isUsbAudioInterface = isUsb,
            connectedDevices = deviceSummaries
        )
    }

    private fun isUsbDevice(info: AudioDeviceInfo): Boolean {
        return info.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            info.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            info.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    }

    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Interface"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset / DAC"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Stereo"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
        else -> "Audio Output"
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {
        }
    }
}

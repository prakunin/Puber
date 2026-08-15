package com.kino.puber.data.repository

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import com.kino.puber.R
import com.kino.puber.data.api.KinoPubApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val UHD_WIDTH = 3840
private const val UHD_HEIGHT = 2160
private const val HEVC_MIME_TYPE = "video/hevc"

internal class DeviceInfoRepository(
    private val context: Context,
    private val apiClient: KinoPubApiClient,
) : IDeviceInfoRepository {
    override fun is4kSupported(): Boolean {
        return isDisplay4kSupported() || is4kHardwareDecoderSupported()
    }

    private fun isDisplay4kSupported(): Boolean {
        val display = getPrimaryDisplay(context)
        val modes = display?.supportedModes ?: return false
        return modes.any { mode ->
            mode.physicalWidth >= UHD_WIDTH && mode.physicalHeight >= UHD_HEIGHT
        }
    }

    private fun is4kHardwareDecoderSupported(): Boolean {
        return hevcDecoders().filter { it.isHardwareDecoder }.any { codec ->
            codec.getCapabilitiesForType(HEVC_MIME_TYPE)
                .videoCapabilities
                ?.isSizeSupported(UHD_WIDTH, UHD_HEIGHT) == true
        }
    }

    /** Every non-encoder codec on the device that advertises HEVC. */
    private fun hevcDecoders(): List<MediaCodecInfo> {
        return MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { codec ->
            !codec.isEncoder && codec.supportedTypes.any { it.equals(HEVC_MIME_TYPE, ignoreCase = true) }
        }
    }

    private val MediaCodecInfo.isHardwareDecoder: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHardwareAccelerated
        } else {
            // Before Q there is no flag, so the software codecs are recognised by name.
            !name.contains("omx.google", ignoreCase = true)
        }

    override fun isHdrSupported(): Boolean {
        return isDisplayHdrSupported() && isHdrCodecSupported()
    }

    private fun isDisplayHdrSupported(): Boolean {
        val display = getPrimaryDisplay(context) ?: return false
        val hdrTypes = display.hdrCapabilities?.supportedHdrTypes ?: return false
        return hdrTypes.isNotEmpty()
    }

    private fun isHdrCodecSupported(): Boolean {
        return hevcDecoders().any { codec ->
            codec.getCapabilitiesForType(HEVC_MIME_TYPE).profileLevels.any { profileLevel ->
                profileLevel.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                    profileLevel.profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt
            }
        }
    }

    override fun isSslSupported(): Boolean = true

    override fun isHevcHardwareDecodingSupported(): Boolean {
        return hevcDecoders().any { it.isHardwareDecoder }
    }

    override fun getAndroidVersion(): String =
        "Android ${Build.VERSION.RELEASE ?: "Unknown"}"

    override fun getDeviceBrand(): String = "${Build.MANUFACTURER}"

    override fun getDeviceModel(): String = "${Build.MODEL}"

    override fun getAppName(): String = context.getString(R.string.app_name)

    override fun saveDeviceInformation(title: String, hardware: String, software: String): Flow<Unit> = flow {
        val result = apiClient.updateDeviceInfo(title, hardware, software)
        emit(result.getOrThrow())
    }

    private fun getPrimaryDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    }
}
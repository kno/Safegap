package com.safegap.camera

import android.graphics.Bitmap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class CameraFrame(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val rotationDegrees: Int,
)

@Singleton
class FrameProducer @Inject constructor() {

    /** Pre-allocated bitmap pool to avoid per-frame allocation. */
    val bitmapPool = BitmapPool(width = 640, height = 480, poolSize = 3)

    private val _frames = MutableSharedFlow<CameraFrame>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<CameraFrame> = _frames.asSharedFlow()

    /**
     * Emit a pre-built frame. The caller is responsible for extracting the bitmap
     * and closing the ImageProxy before calling this method.
     */
    suspend fun emit(bitmap: Bitmap, timestampMs: Long, rotationDegrees: Int) {
        val frame = CameraFrame(
            bitmap = bitmap,
            timestampMs = timestampMs,
            rotationDegrees = rotationDegrees,
        )
        _frames.emit(frame)
    }
}

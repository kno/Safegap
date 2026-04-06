package com.safegap.camera

import android.graphics.Bitmap

/**
 * Thread-safe pool of pre-allocated Bitmaps to avoid per-frame allocation.
 *
 * Acquire a bitmap before converting an ImageProxy, and release it after
 * the detection pipeline has finished processing the frame.
 */
class BitmapPool(private val width: Int, private val height: Int, poolSize: Int = 3) {
    private val pool = ArrayDeque<Bitmap>(poolSize)

    init {
        repeat(poolSize) {
            pool.addLast(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        }
    }

    @Synchronized
    fun acquire(): Bitmap =
        pool.removeFirstOrNull() ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    @Synchronized
    fun release(bitmap: Bitmap) {
        if (bitmap.width == width && bitmap.height == height && !bitmap.isRecycled) {
            pool.addLast(bitmap)
        }
    }
}

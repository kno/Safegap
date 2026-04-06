package com.safegap.app.di

import com.safegap.camera.BitmapPool
import com.safegap.camera.FrameProducer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    @Provides
    @Singleton
    fun provideBitmapPool(frameProducer: FrameProducer): BitmapPool =
        frameProducer.bitmapPool
}

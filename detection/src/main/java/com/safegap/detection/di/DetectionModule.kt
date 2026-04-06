package com.safegap.detection.di

import com.safegap.detection.ObjectDetector
import com.safegap.detection.ObjectDetectorApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DetectionModule {

    @Binds
    abstract fun bindObjectDetectorApi(impl: ObjectDetector): ObjectDetectorApi
}

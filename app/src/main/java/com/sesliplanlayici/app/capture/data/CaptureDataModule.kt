package com.sesliplanlayici.app.capture.data

import com.sesliplanlayici.app.capture.domain.SpeechToText
import com.sesliplanlayici.app.capture.domain.TaskExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureDataModule {

    @Binds
    abstract fun bindSpeechToText(impl: AndroidSpeechToText): SpeechToText

    @Binds
    abstract fun bindTaskExtractor(impl: ProxyTaskExtractor): TaskExtractor
}

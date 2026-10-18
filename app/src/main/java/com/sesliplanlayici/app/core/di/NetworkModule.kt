package com.sesliplanlayici.app.core.di

import com.sesliplanlayici.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Base URL of the AI proxy, from local.properties → proxy.baseUrl (empty when not set). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProxyBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        // Status codes are mapped by the caller.
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
    }

    @Provides
    @ProxyBaseUrl
    fun provideProxyBaseUrl(): String = BuildConfig.PROXY_BASE_URL

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

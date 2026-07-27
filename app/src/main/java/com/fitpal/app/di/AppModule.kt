package com.fitpal.app.di

import android.content.Context
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.ml.IngredientEngine
import com.fitpal.app.ml.LlmIngredientEngine
import com.fitpal.app.ml.ModelManager
import com.fitpal.app.ml.NetworkMonitor
import com.fitpal.app.ml.RemoteIngredientEngine
import com.fitpal.app.ml.RoutingIngredientEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // On-device Gemma engine — the offline fallback that always works once downloaded.
    @Provides
    @Singleton
    fun provideLocalEngine(
        @ApplicationContext context: Context,
        modelManager: ModelManager
    ): LlmIngredientEngine = LlmIngredientEngine(context, modelManager)

    // The IngredientEngine everyone else gets is the router: it prefers online (Gemini)
    // when a key is set and the device is online, and silently falls back to on-device.
    // RemoteIngredientEngine, GeminiClient and NetworkMonitor are @Inject @Singleton, so
    // Hilt builds them automatically.
    @Provides
    @Singleton
    fun provideIngredientEngine(
        remote: RemoteIngredientEngine,
        local: LlmIngredientEngine,
        settings: SettingsRepository,
        network: NetworkMonitor
    ): IngredientEngine = RoutingIngredientEngine(remote, local, settings, network)
}

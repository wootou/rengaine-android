package app.rengaine.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import app.rengaine.BuildConfig
import app.rengaine.data.local.AccountDao
import app.rengaine.data.local.RengaineDatabase
import app.rengaine.data.remote.RetryInterceptor
import app.rengaine.data.remote.UserAgentInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/** Portée qui vit aussi longtemps que l'app (travaux qui survivent à un écran). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Client HTTP de base, partagé par l'API, le lecteur et les pochettes
     * (S3) : délais 10 s / 30 s, nouvelle tentative sur coupure.
     *
     * Journal réseau : en debug seulement, niveau BASIC (ligne de requête et
     * statut, jamais les corps : mots de passe et jetons y passent). Et
     * `Authorization` masqué au cas où le niveau serait monté un jour.
     */
    @Provides
    @Singleton
    fun provideBaseOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                UserAgentInterceptor(
                    "Rengaine/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})",
                ),
            )
            .addInterceptor(RetryInterceptor(maxRetries = 2))
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                    redactHeader("Cookie")
                    redactHeader("Set-Cookie")
                },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RengaineDatabase =
        Room.databaseBuilder(context, RengaineDatabase::class.java, "rengaine.db").build()

    @Provides
    fun provideAccountDao(db: RengaineDatabase): AccountDao = db.accountDao()
}

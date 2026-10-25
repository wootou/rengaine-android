package app.rengaine

import android.app.Application
import app.rengaine.data.remote.AuthorizedCallFactory
import app.rengaine.data.repository.HistoryRepository
import app.rengaine.playback.InfiniteMode
import app.rengaine.playback.QueueSync
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RengaineApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var callFactory: AuthorizedCallFactory
    @Inject lateinit var queueSync: QueueSync
    @Inject lateinit var history: HistoryRepository

    // Créé dès le démarrage pour suivre le lecteur, même sans écran ouvert.
    @Inject lateinit var infiniteMode: InfiniteMode

    override fun onCreate() {
        super.onCreate()
        // File d'attente du compte actif : reprise au démarrage, puis envois (F6).
        queueSync.start()
        // Écoutes restées sur le téléphone (hors ligne, app fermée) : envoyées (F11).
        history.start()
    }

    /**
     * Les images du serveur demandent le jeton du compte (contrat :
     * `bearerAuth` sur `/images/{hash}`) : Coil passe par le même client que
     * le lecteur, qui ne pose le jeton que sur l'adresse de son serveur.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { callFactory })) }
            .build()
}

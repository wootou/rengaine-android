package app.rengaine.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Réglages locaux de l'app, propres à ce téléphone (pas au compte) :
 * compte actif, nom de l'appareil choisi à la connexion, taille du cache,
 * forme d'onde et curseur de volume masqués.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.sessionDataStore

    val activeAccountId: Flow<Long?> = store.data.map { it[ACTIVE_ACCOUNT] }

    suspend fun activeAccountIdNow(): Long? = activeAccountId.first()

    suspend fun setActiveAccount(id: Long?) {
        store.edit { prefs ->
            if (id == null) prefs.remove(ACTIVE_ACCOUNT) else prefs[ACTIVE_ACCOUNT] = id
        }
    }

    /** Dernier nom d'appareil saisi, proposé à la connexion suivante. */
    suspend fun deviceName(): String? = store.data.first()[DEVICE_NAME]

    suspend fun setDeviceName(name: String) {
        store.edit { it[DEVICE_NAME] = name }
    }

    /** Taille maximale du cache des flux, en Mo (Réglages › Sur cet appareil). */
    val cacheSizeMb: Flow<Int> = store.data.map { it[CACHE_SIZE_MB] ?: DEFAULT_CACHE_SIZE_MB }

    suspend fun setCacheSizeMb(mb: Int) {
        store.edit { it[CACHE_SIZE_MB] = mb }
    }

    /**
     * Forme d'onde masquée dans le lecteur sur ce téléphone (U12) ; affichée
     * par défaut, comme `rengaine.ondeMasquee` du site.
     */
    val waveformHidden: Flow<Boolean> = store.data.map { it[WAVEFORM_HIDDEN] ?: false }

    suspend fun setWaveformHidden(hidden: Boolean) {
        store.edit { it[WAVEFORM_HIDDEN] = hidden }
    }

    /** Curseur de volume masqué dans le lecteur sur ce téléphone ; affiché par défaut. */
    val volumeBarHidden: Flow<Boolean> = store.data.map { it[VOLUME_BAR_HIDDEN] ?: false }

    suspend fun setVolumeBarHidden(hidden: Boolean) {
        store.edit { it[VOLUME_BAR_HIDDEN] = hidden }
    }

    /** Albums affichés en liste plutôt qu'en grille de pochettes (propre à ce téléphone). */
    val albumsAsList: Flow<Boolean> = store.data.map { it[ALBUMS_AS_LIST] ?: false }

    suspend fun setAlbumsAsList(asList: Boolean) {
        store.edit { it[ALBUMS_AS_LIST] = asList }
    }

    companion object {
        private val ALBUMS_AS_LIST = booleanPreferencesKey("albums_as_list")
        private val WAVEFORM_HIDDEN = booleanPreferencesKey("waveform_hidden")
        private val VOLUME_BAR_HIDDEN = booleanPreferencesKey("volume_bar_hidden")
        const val DEFAULT_CACHE_SIZE_MB = 500
        private val CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
        private val ACTIVE_ACCOUNT = longPreferencesKey("active_account_id")
        private val DEVICE_NAME = stringPreferencesKey("device_name")
    }
}

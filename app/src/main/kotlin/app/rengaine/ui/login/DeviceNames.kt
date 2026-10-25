package app.rengaine.ui.login

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Nom proposé pour cet appareil à la connexion (`deviceName`, obligatoire).
 * C'est lui qui apparaît dans la liste des appareils connectés du site :
 * d'abord le nom choisi par l'utilisateur dans les réglages d'Android
 * (« Téléphone de Camille »), sinon fabricant et modèle (« OnePlus GM1911 »).
 */
object DeviceNames {
    const val MAX_LENGTH = 100

    fun default(context: Context): String {
        val userName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.trim()
        if (!userName.isNullOrEmpty()) return userName.take(MAX_LENGTH)
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val name = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
        return name.trim().take(MAX_LENGTH)
    }
}

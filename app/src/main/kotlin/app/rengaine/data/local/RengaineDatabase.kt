package app.rengaine.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base locale. Version 1 tant que rien n'est publié : le schéma peut
 * encore changer librement (pas de migration avant le premier APK diffusé).
 * Les tables de la file (F6) et des écoutes en attente (F11) s'ajouteront.
 */
@Database(
    entities = [AccountEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RengaineDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
}

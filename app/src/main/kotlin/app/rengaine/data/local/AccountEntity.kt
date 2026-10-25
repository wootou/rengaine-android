package app.rengaine.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Un compte mémorisé : un utilisateur sur un serveur. Plusieurs comptes
 * peuvent être mémorisés, un seul est actif (décision du mainteneur).
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["serverRoot", "userId"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Adresse normalisée du serveur (`ServerAddress.root`). */
    val serverRoot: String,
    /** Nom annoncé par `GET /server` (« Rengaine », ou celui choisi par l'admin). */
    val serverName: String,
    val apiVersion: String,
    val userId: Long,
    val email: String,
    val displayName: String,
    val role: String,
    /** Identifiant du jeton côté serveur (liste des appareils). */
    val tokenId: Long?,
    /** Jeton chiffré par [TokenCipher] ; jamais en clair. */
    val encryptedToken: String,
    val deviceName: String,
    /** Jeton refusé par le serveur (`401`) : ce compte doit se reconnecter. */
    val needsReauth: Boolean = false,
    val addedAt: Long,
    val lastUsedAt: Long,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY lastUsedAt DESC")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE serverRoot = :serverRoot AND userId = :userId")
    suspend fun find(serverRoot: String, userId: Long): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    @Query("UPDATE accounts SET needsReauth = :needsReauth WHERE id = :id")
    suspend fun setNeedsReauth(id: Long, needsReauth: Boolean)

    @Query("UPDATE accounts SET lastUsedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

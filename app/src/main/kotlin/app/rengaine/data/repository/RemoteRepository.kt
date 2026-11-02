package app.rengaine.data.repository

import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.apiCallUnit
import app.rengaine.data.remote.dto.QueueShareCreatedDto
import app.rengaine.data.remote.dto.QueueShareDto
import app.rengaine.data.remote.dto.QueueShareInputDto
import app.rengaine.data.remote.dto.QueueShareRightsDto
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Liens de télécommande du compte actif (FUT-02, `/me/shares`). */
@Singleton
class RemoteRepository @Inject constructor(private val accounts: AccountRepository) {

    suspend fun shares(): ApiResult<List<QueueShareDto>> {
        val s = accounts.activeSession() ?: return noAccount()
        return apiCall { s.api.listShares() }.map { it.items }
    }

    /** [label] vide : le serveur met « Invité ». */
    suspend fun create(label: String, hours: Int, rights: QueueShareRightsDto): ApiResult<QueueShareCreatedDto> {
        val s = accounts.activeSession() ?: return noAccount()
        val input = QueueShareInputDto(label = label.trim().take(100).ifEmpty { null }, hours = hours, rights = rights)
        return apiCall { s.api.createShare(input) }
    }

    suspend fun revoke(id: Long): ApiResult<Unit> {
        val s = accounts.activeSession() ?: return noAccount()
        return apiCallUnit { s.api.revokeShare(id) }
    }

    private fun noAccount() = ApiResult.Err(ApiError.Network(IOException("Aucun compte")))
}

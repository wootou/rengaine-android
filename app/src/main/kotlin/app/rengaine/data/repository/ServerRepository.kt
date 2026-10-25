package app.rengaine.data.repository

import app.rengaine.data.remote.ApiClients
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.dto.ServerInfoDto
import app.rengaine.domain.ApiCompatibility
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat de la vérification d'une adresse de serveur (F1). */
sealed interface ServerCheck {
    data class Ready(val address: ServerAddress, val info: ServerInfoDto) : ServerCheck

    data class Incompatible(val address: ServerAddress, val apiVersion: String, val serverTooOld: Boolean) :
        ServerCheck

    data object InvalidAddress : ServerCheck
    data object Unreachable : ServerCheck
    data object CertificateRejected : ServerCheck

    /** Quelque chose répond, mais pas un serveur Rengaine. */
    data object NotRengaine : ServerCheck

    /** Serveur Rengaine en difficulté (`5xx`, `429`). */
    data class ServerError(val status: Int, val requestId: String?) : ServerCheck
}

@Singleton
class ServerRepository @Inject constructor(
    private val clients: ApiClients,
) {
    suspend fun check(input: String): ServerCheck =
        when (val parsed = ServerAddress.parse(input)) {
            ServerAddress.ParseResult.Empty, ServerAddress.ParseResult.Invalid -> ServerCheck.InvalidAddress
            is ServerAddress.ParseResult.Valid -> check(parsed.address)
        }

    suspend fun check(address: ServerAddress): ServerCheck =
        when (val result = apiCall { clients.anonymous(address).getServer() }) {
            is ApiResult.Ok -> when (ApiCompatibility.check(result.value.apiVersion)) {
                ApiCompatibility.Verdict.Compatible -> ServerCheck.Ready(address, result.value)
                ApiCompatibility.Verdict.ServerTooOld ->
                    ServerCheck.Incompatible(address, result.value.apiVersion, serverTooOld = true)
                ApiCompatibility.Verdict.ServerTooNew ->
                    ServerCheck.Incompatible(address, result.value.apiVersion, serverTooOld = false)
                ApiCompatibility.Verdict.Unknown -> ServerCheck.NotRengaine
            }
            is ApiResult.Err -> when (val e = result.error) {
                is ApiError.Network -> ServerCheck.Unreachable
                is ApiError.Tls -> ServerCheck.CertificateRejected
                is ApiError.Malformed -> ServerCheck.NotRengaine
                is ApiError.Http ->
                    if (e.status == 404) ServerCheck.NotRengaine else ServerCheck.ServerError(e.status, e.requestId)
            }
        }
}

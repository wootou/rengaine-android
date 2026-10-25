package app.rengaine.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.rengaine.R
import app.rengaine.data.repository.ServerCheck
import app.rengaine.data.repository.SignInError

/** Message d'un problème d'adresse de serveur ; `null` si le serveur est prêt. */
@Composable
fun ServerCheck.problemMessage(): String? = when (this) {
    is ServerCheck.Ready -> null
    ServerCheck.InvalidAddress -> stringResource(R.string.server_invalid)
    ServerCheck.Unreachable -> stringResource(R.string.server_unreachable)
    ServerCheck.CertificateRejected -> stringResource(R.string.server_certificate)
    ServerCheck.NotRengaine -> stringResource(R.string.server_not_rengaine)
    is ServerCheck.Incompatible ->
        if (serverTooOld) {
            stringResource(R.string.server_too_old, apiVersion)
        } else {
            stringResource(R.string.server_too_new, apiVersion)
        }
    is ServerCheck.ServerError -> stringResource(R.string.server_error, status)
}

/** Message d'un échec de connexion, identifiants ou QR. */
@Composable
fun SignInError.message(): String = when (this) {
    SignInError.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
    SignInError.InvalidCode -> stringResource(R.string.error_invalid_code)
    SignInError.AccountPending -> stringResource(R.string.error_account_pending)
    SignInError.AccountSuspended -> stringResource(R.string.error_account_suspended)
    is SignInError.AccountLocked -> retryAfterSeconds?.let { seconds ->
        val minutes = ((seconds + 59) / 60).toInt().coerceAtLeast(1)
        pluralStringResource(R.plurals.error_account_locked_minutes, minutes, minutes)
    } ?: stringResource(R.string.error_account_locked)
    is SignInError.RateLimited -> stringResource(R.string.error_rate_limited)
    is SignInError.Server -> check.problemMessage() ?: stringResource(R.string.error_unexpected_generic)
    SignInError.Unreachable -> stringResource(R.string.server_unreachable)
    SignInError.CertificateRejected -> stringResource(R.string.server_certificate)
    is SignInError.Unexpected -> buildString {
        append(
            if (status != null) {
                stringResource(R.string.error_unexpected, status)
            } else {
                stringResource(R.string.error_unexpected_generic)
            },
        )
        if (requestId != null) {
            append('\n')
            append(stringResource(R.string.error_request_id, requestId))
        }
    }
}

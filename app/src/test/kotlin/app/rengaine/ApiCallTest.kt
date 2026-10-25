package app.rengaine

import app.rengaine.data.remote.ApiClients
import app.rengaine.data.remote.ApiError
import app.rengaine.data.remote.ApiResult
import app.rengaine.data.remote.ErrorCodes
import app.rengaine.data.remote.ServerAddress
import app.rengaine.data.remote.apiCall
import app.rengaine.data.remote.apiCallUnit
import app.rengaine.data.remote.dto.LoginRequest
import app.rengaine.data.remote.dto.Role
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lecture des réponses du serveur selon le contrat (S3). */
class ApiCallTest {
    private lateinit var server: MockWebServer
    private lateinit var clients: ApiClients
    private var address: ServerAddress = ServerAddress.fromStored("https://exemple.invalid")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        clients = ApiClients(OkHttpClient())
        // MockWebServer parle HTTP : on construit l'adresse sans passer par
        // parse(), qui refuse http://.
        address = ServerAddress.fromStored(server.url("/sous/chemin").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `champs et valeurs inconnus sont toleres`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"name":"Rengaine","apiVersion":"2.21.0","registrationOpen":true,
                "features":{"transcoding":true,"continuousPlay":true,"analysis":{"bpm":true,"nouveau":true},
                "favorites":true,"history":true,"pairing":true,"passwordReset":false,"futur":42},
                "limits":{"maxPageSize":500,"maxQueueLength":5000},"champInconnu":{"a":1}}""",
            ),
        )
        val result = apiCall { clients.anonymous(address).getServer() }
        val info = (result as ApiResult.Ok).value
        assertEquals("2.21.0", info.apiVersion)
        assertTrue(info.features.analysis.bpm)
        assertEquals("/sous/chemin/api/v2/server", server.takeRequest().path)
    }

    @Test
    fun `role inconnu devient UNKNOWN`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"user":{"id":3,"email":"a@b.fr","displayName":"A","role":"curateur"},"token":"t","tokenId":9}""",
            ),
        )
        val result = apiCall { clients.anonymous(address).login(LoginRequest("a@b.fr", "x", "Téléphone")) }
        assertEquals(Role.UNKNOWN, (result as ApiResult.Ok).value.user.role)
        val sent = server.takeRequest().body.readUtf8()
        assertTrue("client app envoyé", sent.contains("\"client\":\"app\""))
        assertTrue(sent.contains("\"deviceName\":\"Téléphone\""))
    }

    @Test
    fun `erreur au format du contrat`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(423).setHeader("Retry-After", "120").setBody(
                """{"error":{"status":423,"code":"account_locked","message":"Compte verrouillé","requestId":"01JAD"}}""",
            ),
        )
        val result = apiCall { clients.anonymous(address).getServer() }
        val error = (result as ApiResult.Err).error as ApiError.Http
        assertEquals("account_locked", error.code)
        assertEquals(120L, error.retryAfterSeconds)
        assertEquals("01JAD", error.requestId)
    }

    @Test
    fun `une page qui n'est pas l'API est signalee`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not found</html>"))
        val result = apiCall { clients.anonymous(address).getServer() }
        assertTrue((result as ApiResult.Err).error is ApiError.Malformed)

        server.enqueue(MockResponse().setBody("<html>accueil</html>"))
        val second = apiCall { clients.anonymous(address).getServer() }
        assertTrue((second as ApiResult.Err).error is ApiError.Malformed)
    }

    @Test
    fun `reponse 204 sans corps`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = apiCallUnit { clients.anonymous(address).logout() }
        assertTrue(result is ApiResult.Ok)
    }

    @Test
    fun `serveur injoignable`() = runTest {
        server.shutdown()
        val result = apiCall { clients.anonymous(address).getServer() }
        assertTrue((result as ApiResult.Err).error is ApiError.Network)
    }

    @Test
    fun `mauvais mot de passe ne deconnecte pas le compte`() = runTest {
        var unauthorized = 0
        val api = clients.forAccount(1, address, "jeton", onUnauthorized = { unauthorized++ }).api
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"invalid_credentials","message":"Mot de passe incorrect"}}"""))
        val result = apiCallUnit { api.deleteMe(app.rengaine.data.remote.dto.DeleteMeRequest("faux")) }
        assertEquals(0, unauthorized)
        // Le corps reste lisible : l'écran affiche « Mot de passe incorrect ».
        assertEquals(ErrorCodes.INVALID_CREDENTIALS, ((result as ApiResult.Err).error as ApiError.Http).code)
    }

    @Test
    fun `jeton refuse deconnecte le compte`() = runTest {
        var unauthorized = 0
        val api = clients.forAccount(2, address, "jeton", onUnauthorized = { unauthorized++ }).api
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"unauthorized","message":"Jeton refusé"}}"""))
        apiCall { api.getMe() }
        assertEquals(1, unauthorized)
    }
}

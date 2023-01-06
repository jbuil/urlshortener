package es.unizar.urlshortener.infrastructure.delivery

import GenerateQRUseCase
import com.google.common.net.HttpHeaders.*
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.*
import jdk.nashorn.internal.ir.annotations.Ignore
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.*
import org.springframework.boot.test.autoconfigure.web.servlet.*
import org.springframework.boot.test.mock.mockito.*
import org.springframework.cache.*
import org.springframework.http.*
import org.springframework.test.context.*
import org.springframework.test.web.servlet.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        RestResponseEntityExceptionHandler::class]
)
class UrlShortenerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase

    @MockBean
    private lateinit var generateQRUseCase: GenerateQRUseCase

    @MockBean
    private lateinit var retrieveQRUseCase: RetrieveQRUseCase

    @MockBean
    private lateinit var shortUrlRepository: ShortUrlRepositoryService

    @MockBean
    private lateinit var fileController: FileController

    @MockBean
    private lateinit var cacheManager: CacheManager
    @Test
    fun `redirectTo returns a redirect when the key exists`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection("http://example.com/"))

        mockMvc.perform(get("/{id}", "key"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://example.com/"))

        verify(logClickUseCase).logClick("key",
            ClickProperties(ip = "127.0.0.1", referrer = "http://example.com/", browser = null, platform = null))
    }

    @Test
    fun `redirectTo returns a not found when the key does not exist`() {
        given(redirectUseCase.redirectTo("key"))
            .willAnswer { throw RedirectionNotFound("key") }

        mockMvc.perform(get("/{id}", "key"))
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.statusCode").value(404))

        verify(logClickUseCase, never()).logClick("key", ClickProperties(ip = "127.0.0.1"))
    }

    @Test
    fun `creates without qr returns a basic redirect if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                wantQR = false,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/"),
            null, properties = ShortUrlProperties(safe = true)))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("wantQR","No")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.qr").value(null))
    }

    @Test
    fun `creates with qr returns a basic redirect if it can compute a hash`() {
        val qr = "http://localhost:8080/f684a3c4/qr"
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                wantQR = true,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/"),
            qr, properties = ShortUrlProperties(safe = true)))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("wantQR","Yes")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.qr").value(qr))
    }

    @Test
    fun `creates returns bad request if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "ftp://example.com/",
                wantQR = false,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willAnswer { throw InvalidUrlException("ftp://example.com/") }

        mockMvc.perform(
            post("/api/link")
                .param("url", "ftp://example.com/")
                .param("wantQR","No")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value(400))
    }
    @Test
    fun `generateQR returns a qr code when the key exists`() {
        given(
            retrieveQRUseCase.retrieveQR("f684a3c4", cacheManager)
        ).willAnswer { "test".toByteArray() }

        val result = mockMvc.perform(get("/f684a3c4/qr"))
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG))

        val content = result.andReturn().response.contentAsByteArray
        assertTrue("test".toByteArray().contentEquals(content))
    }

    @Test
    fun `generateQR returns a not found when the key does not exist`() {
        given(
            retrieveQRUseCase.retrieveQR("random", cacheManager)
        ).willAnswer { throw QrUriNotFound("random") }

        mockMvc.perform(get("/{hash}/qr", "random"))
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.statusCode").value(404))
    }


    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testUrlShortenerIsAsync() {

        runBlocking { // Configuramos el mock para que devuelva una URL acortada
            // cada vez que se le llame con una URL válida
            given(
                createShortUrlUseCase.create(
                    url = "http://google.com",
                    wantQR = false,
                    data = ShortUrlProperties(ip = "127.0.0.1")
                )
            ).willReturn(ShortUrl("f684a3c4", Redirection("http://google.com"),
                null, properties = ShortUrlProperties(safe = true)))

            given(
                createShortUrlUseCase.create(
                    url = "http://facebook.com",
                    wantQR = false,
                    data = ShortUrlProperties(ip = "127.0.0.1")
                )
            ).willReturn(ShortUrl("3c3a4f68", Redirection("http://facebook.com"),
                null, properties = ShortUrlProperties(safe = true)))

            given(
                createShortUrlUseCase.create(
                    url = "http://twitter.com",
                    wantQR = false,
                    data = ShortUrlProperties(ip = "127.0.0.1")
                )
            ).willReturn(ShortUrl("9861d2c7", Redirection("http://twitter.com"),
                null, properties = ShortUrlProperties(safe = true)))

            // Creamos una lista de URLs que queremos acortar
            val urls = listOf(
                "http://google.com",
                "http://facebook.com",
                "http://twitter.com"
            )

            // Creamos una lista para almacenar las tareas
            // de acortamiento
            val tasks = mutableListOf<Job>()

            // Creamos una lista para almacenar los resultados
            // de cada tarea de acortamiento
            val results = mutableListOf<MvcResult>()

            // Iniciamos una tarea para acortar cada una de las URLs
            urls.forEach { url ->
                tasks.add(GlobalScope.launch {
                    // Acortamos la URL y añadimos el resultado a la lista
                    results.add(
                        mockMvc.perform(
                            post("/api/link")
                                .param("url", url)
                                .param("wantQR","No")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        ).andReturn()
                    )
                }
                )
            }

            // Esperamos a que todas las tareas finalicen
            runBlocking {
                tasks.forEach {
                    it.join()
                }
            }

            // Comprobamos si se han acortado todas las URLs
            assertEquals(urls.size, results.size)
        }

    }

    fun testGoogleSafeBrowsingService() {
        // URL de prueba que se considera segura
        val testUrlSafe = "https://www.google.com"
        val testUrlNotSafe = "https://www.zipl.in/construction/slider/up/"

        // Inicializar el servicio de Google Safe Browsing
        val googleSafeBrowsingService = GoogleSafeBrowsingServiceImpl()

        // Llamar al método isSafe del servicio de Google Safe Browsing
        val isSafe = googleSafeBrowsingService.isSafe(testUrlSafe)
        val isNotSafe = googleSafeBrowsingService.isSafe(testUrlNotSafe)
        // Verificar que el resultado sea el esperado
        assertTrue(isSafe)
        assertFalse(isNotSafe)
    }
    @Test
    fun testCreateShortUrlWithUnsafeUrl() {
        runBlocking { // URL de prueba que se considera insegura
            val testUrl = "https://www.zipl.in/construction/slider/up/"

            given(
                createShortUrlUseCase.create(
                    url = testUrl,
                    wantQR = false,
                    data = ShortUrlProperties(ip = "127.0.0.1")
                )
            ).willAnswer { throw UrlNotSafe(testUrl) }
        } }
    @Test
    fun `redirectTo returns a redirect when the key exists and the URL is safe`() {
        given(redirectUseCase.redirectTo("key"))
            .willReturn(Redirection("https://www.example.com"))


        // Act and assert
        mockMvc.perform(get("/{id}", "key"))
            .andDo(print())
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string(LOCATION, "https://www.example.com"))

        verify(logClickUseCase).logClick(
            "key",
            ClickProperties(ip = "127.0.0.1", platform = null, browser = null, referrer = "https://www.example.com")
        )
    }
    @Test
    fun `redirectTo returns a forbidden when the key exists and the URL is not safe`() {
        val shortUrl =
        ShortUrl(
            "key",
            Redirection("https://www.example.com"),
            null,
            properties = (ShortUrlProperties(
            safe = false)))

        given(redirectUseCase.redirectTo("key"))
            .willReturn(Redirection("https://www.example.com"))
        given(shortUrlRepository.findByKey("key"))
            .willReturn(shortUrl)

        // Act and assert
        mockMvc.perform(get("/{id}", "key"))
            .andDo(print())
            .andExpect(status().isForbidden)

    }
    @Test
    fun `redirectTo returns a bad request when the key exists but the URL is not yet verified`() {
        // Arrange
        val shortUrl = ShortUrl("key", Redirection("https://www.example.com"),
                null, properties = (ShortUrlProperties(safe = null)))
        given(redirectUseCase.redirectTo("key"))
            .willReturn(Redirection("https://www.example.com"))
        given(shortUrlRepository.findByKey("key"))
            .willReturn(shortUrl)

        // Act and assert
        mockMvc.perform(get("/{id}", "key"))
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(header().string(RETRY_AFTER, "10000"))
    }
}

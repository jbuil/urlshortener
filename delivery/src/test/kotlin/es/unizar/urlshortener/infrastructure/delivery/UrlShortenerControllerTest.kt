package es.unizar.urlshortener.infrastructure.delivery

import GenerateQRUseCase
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.*
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import javax.xml.bind.JAXBElement

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
    fun `creates returns a basic redirect if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/")))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))
    }

    @Test
    fun `creates returns bad request if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "ftp://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willAnswer { throw InvalidUrlException("ftp://example.com/") }

        mockMvc.perform(
            post("/api/link")
                .param("url", "ftp://example.com/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.statusCode").value(400))
    }

    @Test
    fun `generateQR returns a qr code when the key exists`() {
        given(generateQRUseCase.generateQR("key")).willReturn(ByteArrayResource("test".toByteArray()))

        mockMvc.perform(get("/{hash}/qr", "key"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(content().bytes("test".toByteArray()))
    }

    @Test
    fun `generateQR returns a not found when the key does not exist`() {
        given(generateQRUseCase.generateQR("key")).willAnswer { throw QrUriNotFound("key") }

        mockMvc.perform(get("/{hash}/qr", "key"))
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.statusCode").value(404))
    }


    @Test
    fun testUrlShortenerIsAsync() {

        // Configuramos el mock para que devuelva una URL acortada
        // cada vez que se le llame con una URL válida
        given(
            createShortUrlUseCase.create(
                url = "http://google.com",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://google.com")))

        given(
            createShortUrlUseCase.create(
                url = "http://facebook.com",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("3c3a4f68", Redirection("http://facebook.com")))

        given(
            createShortUrlUseCase.create(
                url = "http://twitter.com",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("9861d2c7", Redirection("http://twitter.com")))

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

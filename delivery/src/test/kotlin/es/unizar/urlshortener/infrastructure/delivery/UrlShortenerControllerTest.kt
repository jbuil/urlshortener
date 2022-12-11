package es.unizar.urlshortener.infrastructure.delivery

import GenerateQRUseCase
import com.google.common.net.HttpHeaders.RETRY_AFTER
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCaseImpl
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import kotlinx.coroutines.*
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.kotlin.*
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
import org.mockito.Mockito.mock
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.http.HttpStatus

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
    private lateinit var shortUrlRepository: ShortUrlRepositoryService

    @MockBean
    private lateinit var fileController: FileController

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
    suspend fun `creates returns a basic redirect if it can compute a hash`() {
        val qr = CreateShortUrlUseCaseImpl.baseURI + "f684a3c4" + CreateShortUrlUseCaseImpl.qrEndpoint
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                wantQR = true,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/"), qr))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.url").value("http://localhost/f684a3c4"))
            .andExpect(jsonPath("$.qr").value(qr))
    }

    @Test
    suspend fun `creates returns bad request if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "ftp://example.com/",
                wantQR = true,
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
            .andExpect(jsonPath("$.qr").value(null))
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


    @OptIn(DelicateCoroutinesApi::class)
    @Test
    suspend fun testUrlShortenerIsAsync() {

        // Configuramos el mock para que devuelva una URL acortada
        // cada vez que se le llame con una URL válida
        given(
            createShortUrlUseCase.create(
                url = "http://google.com",
                wantQR = false,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://google.com"), null))

        given(
            createShortUrlUseCase.create(
                url = "http://facebook.com",
                wantQR = false,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("3c3a4f68", Redirection("http://facebook.com"), null))

        given(
            createShortUrlUseCase.create(
                url = "http://twitter.com",
                wantQR = false,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("9861d2c7", Redirection("http://twitter.com"), null))

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
    fun testRead() {
        val shortUrlRepository = mock(ShortUrlRepositoryService::class.java)
        val rabbitTemplate = RabbitTemplate()
        val rabbitMQService = RabbitMQServiceImpl(rabbitTemplate,shortUrlRepository)
        // Create a ConnectionFactory and set the connection parameters
        val factory = ConnectionFactory()
        factory.host = "localhost"
        factory.username = "guest"
        factory.password = "guest"

        // Create a connection to the RabbitMQ server
        val connection = factory.newConnection()


        // Set up the mock short URL repository to return a sample URL
        val url = "http://example.com"
        val shortUrl = ShortUrl("123456", Redirection(url), null)
        given(shortUrlRepository.findByKey("123456")).willReturn(shortUrl)
        val channel = connection.createChannel()
        val queue = "queue"
        channel.queuePurge(queue)
        // Write a message to the queue
        rabbitMQService.write(url, "123456")

        // Read the message from the queue and verify the contents
        val message = rabbitMQService.read("")
        assertEquals("$url::123456", message)
    }
    @Test
    fun testWrite() {
        val shortUrlRepository = mock(ShortUrlRepositoryService::class.java)
        val rabbitTemplate = RabbitTemplate()
        val rabbitMQService = RabbitMQServiceImpl(rabbitTemplate,shortUrlRepository)
        // Envía un mensaje a una cola de RabbitMQ
        val url = "https://example.com"
        val id = "abc123"
        rabbitMQService.write(url, id)

        // Verifica que el mensaje se haya enviado correctamente
        val factory = ConnectionFactory()
        factory.setUri("amqp://localhost")

        val connection = factory.newConnection()
        val channel = connection.createChannel()

        // Obtiene el mensaje de la cola "queue"
        val queue = "queue"
        val consumer = object : DefaultConsumer(channel) {
            override fun handleDelivery(consumerTag: String, envelope: Envelope,
                                        properties: AMQP.BasicProperties, body: ByteArray) {
                // Verifica que el cuerpo del mensaje sea igual a "$url:$id"
                assertEquals("$url:$id", String(body))
            }
        }
        channel.basicConsume(queue, true, consumer)
    }
    @Test
    fun testRabbitMQService() {
        // Crea un objeto RabbitMQService y configúralo con
        val shortUrlRepository = mock(ShortUrlRepositoryService::class.java)
        val rabbitTemplate = RabbitTemplate()
        val rabbitMQService = RabbitMQServiceImpl(rabbitTemplate,shortUrlRepository)

        // Envía un mensaje de prueba a la cola "queue"
        rabbitMQService.write("Hello, world!", "queue")

        // Comprueba si el mensaje ha sido escrito correctamente en la cola
        //assertTrue(rabbitMQService.read())
    }

    @Test
    suspend fun testRabbitMQServiceIsUsed() {
        // Creamos un mock del servicio de RabbitMQ
        val rabbitMQService = mock(RabbitMQService::class.java)

        // Creamos una instancia del caso de uso que utiliza el mock del servicio de RabbitMQ
        val createShortUrlUseCase = CreateShortUrlUseCaseImpl(
            shortUrlRepository = mock(ShortUrlRepositoryService::class.java),
            validatorService = ValidatorServiceImpl(),
            hashService = HashServiceImpl(),
            rabbitMQService = rabbitMQService)
        // Llamamos al caso de uso con una URL válida
        createShortUrlUseCase.create("http://google.com", false, ShortUrlProperties(ip = "127.0.0.1"))

        // Verificamos que se llama al servicio de RabbitMQ con la URL y el ID correctos
        verify(rabbitMQService).write("http://google.com", "58f3ae21")
    }
    @Test
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
    suspend fun testCreateShortUrlWithUnsafeUrl() {
        // URL de prueba que se considera insegura
        val testUrl = "https://www.zipl.in/construction/slider/up/"

        given(
            createShortUrlUseCase.create(
                url = testUrl,
                wantQR = false,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willAnswer { throw UrlNotSafe(testUrl) }
    }

    suspend fun `when redirectTo is called and short url is not verified then return SERVICE_UNAVAILABLE`() {
        // Arrange
        given(
            createShortUrlUseCase.create(
                url = "http://google.com",
                wantQR = false,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://google.com"), null))

        // Act
        mockMvc.perform(get("/{id}", "key"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string(RETRY_AFTER, notNullValue()))
            .andExpect(jsonPath("$.statusCode").value(HttpStatus.SERVICE_UNAVAILABLE.value()))
            .andExpect(jsonPath("$.message").value("URI de destino no validada todavía"))
    }
    suspend fun `when redirectTo is called and short url is not safe then throw UrlNotSafe`()
    {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                wantQR = false,
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willAnswer { throw InvalidUrlException("ftp://example.com/") }

        mockMvc.perform(get("/{hash}", "key"))
            .andExpect(status().isForbidden)
            .andExpect(content().string("{\"statusCode\": 403, \"message\": \"La url es insegura\"}"))
    }
    @Test
    fun `should return a SERVICE UNAVAILABLE response when the target URL has not been verified`() {
        val urlId = "123456"
        val expectedResponse = ErrorMessage(HttpStatus.SERVICE_UNAVAILABLE.value(), "URI de destino no validada todavía")

       // val response =  RedirectController(mockRedirectUseCase, mockLogClickUseCase, mockShortUrlRepository)


      //  assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
       // assertEquals(expectedResponse, response.body)
    }










}

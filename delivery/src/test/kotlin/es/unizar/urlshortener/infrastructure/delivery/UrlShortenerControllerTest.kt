package es.unizar.urlshortener.infrastructure.delivery

import GenerateQRUseCase
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCaseImpl
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
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
    @Test
    fun testRabbitMQConnection() {
        // Dirección del servidor de RabbitMQ
        val host = "localhost"
        // Puerto del servidor de RabbitMQ
        val port = 5672

        // Crea un objeto ConnectionFactory y establece los parámetros de conexión
        val factory = ConnectionFactory()
        factory.host = host
        factory.port = port
        factory.username = "guest"
        factory.password = "guest"

        // Crea una conexión a RabbitMQ
        val connection = factory.newConnection()

        // Verifica que la conexión se haya establecido correctamente
        assertTrue(connection.isOpen, "Could not connect to RabbitMQ server at $host:$port")
    }
    @Test
    fun testWrite() {
        val rabbitMQService = RabbitMQServiceImpl()
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
    fun testReadMessage() {
        // Crea un objeto ConnectionFactory y establece los parámetros de conexión
        val factory = ConnectionFactory()
        factory.host = "localhost"
        factory.port = 5672
        factory.username = "guest"
        factory.password = "guest"

        // Crea una conexión a RabbitMQ
        val connection = factory.newConnection()

        // Crea un objeto Channel y selecciona un vhost y una cola
        val channel = connection.createChannel()
        val vhost = "/"
        val queue = "queue"
        channel.queueDeclare(queue, false, false, false, null)

        // Crea una instancia de RabbitMQService
        val rabbitMQService = RabbitMQServiceImpl()


        // Envía un mensaje a la cola
        val message = "Hello world!"
        val body = message.toByteArray()
        channel.basicPublish("", queue, null, body)

        // Llamar a read para leer el mensaje enviado
        val receivedMessage = rabbitMQService.read()

        // Comprueba que el mensaje leído sea el mismo que se envió
        assertEquals(message, receivedMessage)
    }
    @Test
    fun testRabbitMQService() {
        // Crea un objeto RabbitMQService y configúralo con
        // los parámetros de conexión adecuados
        val rabbitMQService = RabbitMQServiceImpl()

        // Envía un mensaje de prueba a la cola "queue"
        rabbitMQService.write("Hello, world!", "queue")

        // Comprueba si el mensaje ha sido escrito correctamente en la cola
        //assertTrue(rabbitMQService.read())
    }

    @Test
    fun testRabbitMQServiceIsUsed() {
        // Creamos un mock del servicio de RabbitMQ
        val rabbitMQService = mock(RabbitMQService::class.java)

        // Creamos una instancia del caso de uso que utiliza el mock del servicio de RabbitMQ
        val createShortUrlUseCase = CreateShortUrlUseCaseImpl(
            shortUrlRepository = mock(ShortUrlRepositoryService::class.java),
            validatorService = mock(ValidatorService::class.java),
            hashService = mock(HashService::class.java),
            rabbitMQService = rabbitMQService
        )

        // Llamamos al caso de uso con una URL válida
        createShortUrlUseCase.create("http://google.com", ShortUrlProperties(ip = "127.0.0.1"))

        // Verificamos que se llama al servicio de RabbitMQ con la URL y el ID correctos
        verify(rabbitMQService).write("http://google.com", "f684a3c4")
    }






}

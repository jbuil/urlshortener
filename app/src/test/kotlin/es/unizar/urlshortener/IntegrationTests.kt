package es.unizar.urlshortener

import com.fasterxml.jackson.databind.JsonNode
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCaseImpl
import es.unizar.urlshortener.infrastructure.delivery.*
import kotlinx.coroutines.runBlocking
import net.minidev.json.JSONValue
import net.minidev.json.JSONArray
import net.minidev.json.JSONObject
import org.apache.http.impl.client.HttpClientBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.*
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.query
import org.springframework.test.jdbc.JdbcTestUtils
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.test.web.servlet.MockMvc
import java.net.URI


@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class HttpRequestTest {
    @LocalServerPort
    private val port = 0

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @BeforeEach
    fun setup() {
        val httpClient = HttpClientBuilder.create()
            .disableRedirectHandling()
            .build()
        (restTemplate.restTemplate.requestFactory as HttpComponentsClientHttpRequestFactory).httpClient = httpClient

        JdbcTestUtils.deleteFromTables(jdbcTemplate, "shorturl", "click")
    }

    @AfterEach
    fun tearDowns() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "shorturl", "click")
    }

    @Test
    fun `main page works`() {
        val response = restTemplate.getForEntity("http://localhost:$port/", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("A front-end example page for the project")
    }

    @Test
    fun `redirectTo returns a redirect when the key exists`() {
        val target = shortUrl("http://example.com/").headers.location
        require(target != null)

        val response = restTemplate.getForEntity(target, String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.TEMPORARY_REDIRECT)
        assertThat(response.headers.location).isEqualTo(URI.create("http://example.com/"))

        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(1)
    }

    @Test
    fun `redirectTo returns a not found when the key does not exist`() {
        val response = restTemplate.getForEntity("http://localhost:$port/f684a3c4", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)

        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(0)
    }

    @Test
    fun `creates returns a basic redirect if it can compute a hash`() {
        val response = shortUrl("http://example.com/")

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.headers.location).isEqualTo(URI.create("http://localhost:$port/f684a3c4"))
        print(response.body)
        assertThat(response.body?.url).isEqualTo(URI.create("http://localhost:$port/f684a3c4"))

        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "shorturl")).isEqualTo(1)
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(0)
    }

    @Test
    fun `creates returns bad request if it can't compute a hash`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val data: MultiValueMap<String, String> = LinkedMultiValueMap()
        data["url"] = "ftp://example.com/"
        data["wantQR"] = "No"

        val response = restTemplate.postForEntity(
            "http://localhost:$port/api/link",
            HttpEntity(data, headers), ShortUrlDataOut::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)

        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "shorturl")).isEqualTo(0)
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(0)
    }

    private fun shortUrl(url: String): ResponseEntity<ShortUrlDataOut> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val data: MultiValueMap<String, String> = LinkedMultiValueMap()
        data["url"] = url
        data["wantQR"] = "No"

        return restTemplate.postForEntity(
            "http://localhost:$port/api/link",
            HttpEntity(data, headers), ShortUrlDataOut::class.java
        )
    }

    @Test
    fun `infoHTTPHeader correcto`(){
        val respHeaders = shortUrl("https://www.youtube.com")

        val target = respHeaders.headers.location
        require(target != null)

        restTemplate.getForEntity(target, String::class.java)
        val hash = target.toString().split("/")[3]

        val response1 = restTemplate.getForEntity("http://localhost:$port/api/link/"+hash, String::class.java)

        // Get the response body from the ResponseEntity object
        val responseBody = response1.body
        // Parse the response body into a JSONArray
        val jsonArray = JSONValue.parse(responseBody) as JSONArray

        // Get the first element from the JSONArray (which should be a JSONObject)
        val jsonObject = jsonArray.get(0) as JSONObject
        // Get the "hash" value from the JSONObject
        val id = jsonObject.get("hash")

        // Get the "browser" value from the JSONObject
        val browser = jsonObject.get("browser")

        // Get the "platform" value from the JSONObject
        val platform = jsonObject.get("platform")

        assertThat(browser).isEqualTo("Apache-HttpClient 4.5.13")
        assertThat(id).isEqualTo("e7f83ee8")
        assertThat(platform).isEqualTo("Other")
    }

    fun extractHash(url: String): String {
        val regex = Regex("http://localhost:\\d+/(\\w+)")
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1) ?: ""
    }

    @Test
    fun `clicks are recorded when a short URL is clicked`() {
        // Creamos una URL acortada
        val shortUrl = shortUrl("http://example.com/")

        // Comprobamos que se ha creado correctamente
        assertThat(shortUrl.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(shortUrl.headers.location).isEqualTo(URI.create("http://localhost:$port/f684a3c4"))
        assertThat(shortUrl.body?.url).isEqualTo(URI.create("http://localhost:$port/f684a3c4"))

        // Comprobamos que se ha guardado en la base de datos
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "shorturl")).isEqualTo(1)
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(0)

        // Hacemos clic en la URL acortada
        mockMvc.perform(MockMvcRequestBuilders.get("/f684a3c4")
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:80.0) Gecko/20100101 Firefox/80.0")
                .header("X-Forwarded-For", "127.0.0.1")
        ).andReturn()


        // Comprobamos que se ha registrado el clic
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(1)

        // Recupera la fila de la tabla 'click'
        val clickRow = jdbcTemplate.queryForMap("SELECT * FROM click LIMIT 1")
        val url = shortUrl.body?.url
        val hash = extractHash(url.toString())

        // Verifica que la información del clic sea correcta
        assertThat(clickRow["hash"]).isEqualTo(hash) // el ID del enlace acortado debe ser el mismo que se devolvió en la respuesta anterior
        assertThat(clickRow["browser"]).isEqualTo("Firefox 80.0") // o el nombre del navegador que se esté utilizando
        assertThat(clickRow["platform"]).isEqualTo("Linux") // o el nombre de la plataforma que se esté utilizando
        assertThat(clickRow["created"]).isNotNull() // la fecha debe tener un valor
    }
    fun testRead() {
        val shortUrlRepository = Mockito.mock(ShortUrlRepositoryService::class.java)
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
        BDDMockito.given(shortUrlRepository.findByKey("123456")).willReturn(shortUrl)
        val channel = connection.createChannel()
        val queue = "queue"
        channel.queuePurge(queue)
        // Write a message to the queue
        rabbitMQService.write(url, "123456")

        // Read the message from the queue and verify the contents
        val message = rabbitMQService.read("")
        Assertions.assertEquals("$url::123456", message)
    }
    @Test
    fun testWrite() {
        val shortUrlRepository = Mockito.mock(ShortUrlRepositoryService::class.java)
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

        // Obtiene el mensaje de la cola "safe"
        val queue = "safe"
        val consumer = object : DefaultConsumer(channel) {
            override fun handleDelivery(consumerTag: String, envelope: Envelope,
                                        properties: AMQP.BasicProperties, body: ByteArray) {
                // Verifica que el cuerpo del mensaje sea igual a "$url:$id"
                Assertions.assertEquals("$url:$id", String(body))
            }
        }
        channel.basicConsume(queue, true, consumer)
    }
    @Test
    fun testRabbitMQService() {
        // Crea un objeto RabbitMQService y configúralo con
        val shortUrlRepository = Mockito.mock(ShortUrlRepositoryService::class.java)
        val rabbitTemplate = RabbitTemplate()
        val rabbitMQService = RabbitMQServiceImpl(rabbitTemplate,shortUrlRepository)

        // Envía un mensaje de prueba a la cola "queue"
        rabbitMQService.write("Hello, world!", "safe")
        //assertTrue(rabbitMQService.read())
    }

    @Test
    fun testRabbitMQServiceIsUsed() {
        runBlocking { val rabbitMQService = Mockito.mock(RabbitMQService::class.java)

            // Creamos una instancia del caso de uso que utiliza el mock del servicio de RabbitMQ
            val createShortUrlUseCase = CreateShortUrlUseCaseImpl(
                shortUrlRepository = Mockito.mock(ShortUrlRepositoryService::class.java),
                validatorService = ValidatorServiceImpl(),
                hashService = HashServiceImpl(),
                rabbitMQService = rabbitMQService)
            // Llamamos al caso de uso con una URL válida
            createShortUrlUseCase.create("http://google.com", false, ShortUrlProperties(ip = "127.0.0.1"))

            // Verificamos que se llama al servicio de RabbitMQ con la URL y el ID correctos
            verify(rabbitMQService).write("http://google.com", "58f3ae21")
        } }
    // Creamos un mock del servicio de RabbitMQ


}
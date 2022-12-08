package es.unizar.urlshortener.infrastructure.delivery

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.safebrowsing.Safebrowsing
import com.google.api.services.safebrowsing.model.FindThreatMatchesRequest
import com.google.api.services.safebrowsing.model.FindThreatMatchesResponse
import com.google.api.services.safebrowsing.model.ThreatEntry
import com.google.api.services.safebrowsing.model.ThreatInfo
import com.google.common.hash.Hashing
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import es.unizar.urlshortener.core.*
import io.github.g0dkar.qrcode.*
import net.minidev.json.JSONObject
import org.apache.commons.validator.routines.*
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.core.io.*
import org.springframework.util.MimeTypeUtils.*
import java.io.*
import java.lang.Thread.sleep
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.*


/**
 * Implementation of the port [ValidatorService].
 */
class ValidatorServiceImpl : ValidatorService {
    override fun isValid(url: String) = urlValidator.isValid(url)
    companion object {
        val urlValidator = UrlValidator(arrayOf("http", "https"))
    }
}

/**
 * Implementation of the port [HashService].
 */
@Suppress("UnstableApiUsage")
class HashServiceImpl : HashService {
    override fun hasUrl(url: String) = Hashing.murmur3_32_fixed().hashString(url, StandardCharsets.UTF_8).toString()
}

/**
 * Implementation of the port [QRService].
 */
class QRServiceImpl : QRService {
    override fun qrEncode(hash: String) : ByteArrayResource =
        ByteArrayOutputStream().let{
            QRCode(hash).render().writeImage(it)
            val imageBytes = it.toByteArray()
            ByteArrayResource(imageBytes, IMAGE_PNG_VALUE)
        }
}

class RabbitMQServiceImpl(
    private val shortUrlRepository: ShortUrlRepositoryService
) : RabbitMQService {
    // Crea un objeto ConnectionFactory y establece los parámetros de conexión
    private val factory = ConnectionFactory()

    // Crea una conexión a RabbitMQ
    private val connection = factory.newConnection()


    @RabbitListener(queues = arrayOf("queue"))
    override fun read(message: String) {
        // process the message
        val (url,hash) = message.split("::")
        println(url + hash)
        // Duermes para comprobar la cola de broker
       // sleep(10000)
        shortUrlRepository.updateSafe(hash,GoogleSafeBrowsingServiceImpl().isSafe(url))
    }


    override fun write(url: String, id: String) {
        try {

            // Crea un objeto Channel y selecciona un vhost y una cola
            val channel = connection.createChannel()

            val queue = "queue"
            channel.queueDeclare(queue, false, false, false, null)

            // Envía un mensaje a la cola
            val message = "$url::$id"
            val body = message.toByteArray()
            channel.basicPublish("", queue, null, body)
            println("Sent message: $message")
        } catch (e: Exception) {
            // Maneja cualquier error que ocurra durante la escritura de un mensaje en el broker
        }
    }
}
class GoogleSafeBrowsingServiceImpl: GoogleSafeBrowsingService{

        override fun isSafe(url: String): Boolean {
            // create a JSON object
            val json = JSONObject()
            val client = JSONObject()
            val threatInfo = JSONObject()
            val threatEntry = JSONObject()

            client["clientId"] = "urlshortener-unizar-"
            client["clientVersion"] = "1.5.2"
            json["client"] = client

            threatEntry["url"] = url
            threatInfo["threatTypes"] = arrayOf("THREAT_TYPE_UNSPECIFIED", "MALWARE","SOCIAL_ENGINEERING",
                "UNWANTED_SOFTWARE","MALICIOUS_BINARY","POTENTIALLY_HARMFUL_APPLICATION")
            threatInfo["platformTypes"] = arrayOf("ALL_PLATFORMS")
            threatInfo["threatEntryTypes"] = arrayOf("URL")
            threatInfo["threatEntries"] = arrayOf(threatEntry)
            json["threatInfo"] = threatInfo

            val httpClient = HttpClient.newBuilder().build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://safebrowsing.googleapis.com/v4/threatMatches:find?key=AIzaSyAoDBzPEkXQiqmTtf7vXQp-vtPKXZwf3rU"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            return response.body().toString() == "{}\n"
        }
}










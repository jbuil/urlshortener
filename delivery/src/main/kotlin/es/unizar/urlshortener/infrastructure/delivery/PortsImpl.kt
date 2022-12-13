package es.unizar.urlshortener.infrastructure.delivery

import com.google.common.hash.Hashing
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.infrastructure.delivery.ValidatorServiceImpl.Companion.urlValidator
import io.github.g0dkar.qrcode.*
import kotlinx.coroutines.DelicateCoroutinesApi
import net.minidev.json.JSONObject
import org.apache.commons.validator.routines.*
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.core.io.*
import org.springframework.util.MimeTypeUtils.*
import java.io.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


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
    private val rabbitTemplate: RabbitTemplate,
    private val shortUrlRepository: ShortUrlRepositoryService,
) : RabbitMQService {

    @RabbitListener(queues = arrayOf("queue"))
    override fun read(message: String) {
        val (url,hash) = message.split("::")
        shortUrlRepository.updateSafe(hash, GoogleSafeBrowsingServiceImpl().isSafe(url))

        // Agrega el listener al RabbitTemplate
        //rabbitTemplate.addListener(listener)
    }
    override fun write(url: String, id: String) {
        try {
            // Envía un mensaje a la cola
            val queue = "queue"
            val message = "$url::$id"
            rabbitTemplate.convertAndSend(queue, message)
            println("Mensaje enviado: $message")
        } catch (e: Exception) {
            // Maneja cualquier error que ocurra durante la escritura de un mensaje
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

interface UploadFileService{
    abstract val createShortUrlUseCase: CreateShortUrlUseCase

    suspend fun saveFile(file: MultipartFile): List<String>
}
@Service
class UploadFileServiceImpl(override val createShortUrlUseCase: CreateShortUrlUseCase) : UploadFileService {
    val uploadFolder: String = "..//files//"

    override suspend fun saveFile(file: MultipartFile): List<String>{
        if (!file.isEmpty) {
            val bytes = file.bytes
            val path: Path = Paths.get(uploadFolder + file.originalFilename)
            Files.write(path, bytes)
            var list = Files.readAllLines(path)
            var i = 0
            while (i < list.size) {
                if (urlValidator.isValid(list[i])) {
                    var su = createShortUrlUseCase.create(
                        url = list[i],
                        wantQR = false,
                        data = ShortUrlProperties(ip = "127.0.0.1")
                    )
                    list[i] = ("http://localhost:8080/" + su.hash)
                } else {
                    list[i] = "invalid_URL"
                }
                i += 1
            }
            return list
        } else {
            return emptyList()
        }
    }
}









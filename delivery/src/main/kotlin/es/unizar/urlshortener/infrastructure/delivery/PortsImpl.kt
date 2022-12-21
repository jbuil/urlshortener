package es.unizar.urlshortener.infrastructure.delivery

import com.google.common.hash.*
import com.google.zxing.qrcode.*
import com.opencsv.*
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.*
import es.unizar.urlshortener.infrastructure.delivery.ValidatorServiceImpl.Companion.urlValidator
import net.minidev.json.*
import org.apache.commons.validator.routines.*
import org.springframework.amqp.rabbit.annotation.*
import org.springframework.amqp.rabbit.core.*
import org.springframework.stereotype.*
import org.springframework.web.multipart.*
import java.awt.*
import java.awt.image.*
import java.io.*
import java.net.*
import java.net.http.*
import java.nio.charset.*
import java.nio.file.*
import javax.imageio.*


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
    override fun qrEncode(hash: String) : ByteArray {
        // Crea el código QR usando la función createQrImage()
        val image = createQrImage(hash)

        // Almacena la imagen en un array de bytes para poder almacenarla en la cache
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }
    private fun createQrImage(qrData: String): BufferedImage {
        val width = 300
        val height = 300
        // Crea el código QR usando la biblioteca qrcode
        val qrWriter = QRCodeWriter()
        val qrCode = qrWriter.encode(qrData, com.google.zxing.BarcodeFormat.QR_CODE, width, height)

        // Crea una imagen en blanco para dibujar el código QR
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        // Recorre el BitMatrix y dibuja los pixeles en la imagen
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (qrCode.get(x, y)) {
                    image.setRGB(x, y, Color.WHITE.rgb)
                }
            }
        }

        return image
    }
}

class RabbitMQServiceImpl(
    private val rabbitTemplate: RabbitTemplate,
    private val shortUrlRepository: ShortUrlRepositoryService,

) : RabbitMQService {
    var i = 0
    @RabbitListener(queues = arrayOf("safe"))
    override fun read(message: String) {
        val (url,hash) = message.split("::")
        print(url + hash)
        // Condición para la demo, url not verified
        if(i == 0 && url == "http://www.unizar.es"){
            i++
        }
        else{
            shortUrlRepository.updateSafe(hash, GoogleSafeBrowsingServiceImpl().isSafe(url))
        }


    }
    override fun write(url: String, id: String) {
        // Envía un mensaje a la cola
        val queue = "safe"
        val message = "$url::$id"
        rabbitTemplate.convertAndSend("exchange", "safe", message)
        println("Mensaje enviado: $message")
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
            var list: MutableList<String> = ArrayList<String>()
            var fr = Files.newBufferedReader(path, StandardCharsets.UTF_8)
            var reader = CSVReader(fr)
            var line = reader.readNext()

            while ( line != null) {
                for (i in line) {
                    if (urlValidator.isValid(i)) {
                        var su = createShortUrlUseCase.create(
                            url = i,
                            wantQR = false,
                            data = ShortUrlProperties(ip = "127.0.0.1")
                        )
                        list.add("http://localhost:8080/" + su.hash)
                    } else {
                        list.add("invalid_URL")
                    }
                }
                line = reader.readNext()
            }

            fr.close()
            reader.close()

            return list
        } else {
            return emptyList()
        }
    }
}









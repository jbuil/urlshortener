package es.unizar.urlshortener.infrastructure.delivery

import com.google.common.hash.*
import com.rabbitmq.client.ConnectionFactory
import es.unizar.urlshortener.core.*
import io.github.g0dkar.qrcode.*
import org.apache.commons.validator.routines.*
import org.springframework.core.io.*
import org.springframework.util.MimeTypeUtils.*
import java.io.*
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
    host: String = "localhost",
    port: Int = 5672,
    username: String = "guest",
    password: String = "guest"
) : RabbitMQService {
    override fun read(): String {
        try {
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

            // Lee un mensaje de la cola
            val result = channel.basicGet(queue, true)
            if (result != null) {
                val message = String(result.body, Charsets.UTF_8)
                println("Received message: $message")
                return message
            } else {
                return ""
            }
        } catch (e: Exception) {
            // Maneja cualquier error que ocurra durante la lectura de un mensaje desde el broker
            return "Error durante la lectura de un mensaje desde el broker"
        }
    }

    override fun write(url: String, id: String) {
        try {
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

            // Envía un mensaje a la cola
            val message = "$url:$id"
            val body = message.toByteArray()
            channel.basicPublish("", queue, null, body)
            println("Sent message: $message")
        } catch (e: Exception) {
            // Maneja cualquier error que ocurra durante la escritura de un mensaje en el broker
        }
    }

}






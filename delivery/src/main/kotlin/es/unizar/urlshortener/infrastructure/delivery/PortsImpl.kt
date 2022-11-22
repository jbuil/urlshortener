package es.unizar.urlshortener.infrastructure.delivery

import com.google.common.hash.*
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
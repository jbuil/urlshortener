import es.unizar.urlshortener.core.*
import io.github.g0dkar.qrcode.*
import org.springframework.core.io.*
import org.springframework.util.MimeTypeUtils.*
import java.io.*

interface GenerateQRUseCase {
    fun generateQR(hash: String) : ByteArrayResource
}

class GenerateQRUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService
) : GenerateQRUseCase {
    override fun generateQR(hash: String) : ByteArrayResource =
        shortUrlRepository.findByKey(hash)?.let {
            val imageOut = ByteArrayOutputStream()
            QRCode(hash).render().writeImage(imageOut)
            val imageBytes = imageOut.toByteArray()
            ByteArrayResource(imageBytes, IMAGE_PNG_VALUE)
        } ?: throw QrUriNotFound(hash)
}
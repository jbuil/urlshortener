import es.unizar.urlshortener.core.*
import org.springframework.core.io.*

interface GenerateQRUseCase {
    fun generateQR(hash: String) : ByteArrayResource
}

class GenerateQRUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService,
    private val qrService: QRService
) : GenerateQRUseCase {
    override fun generateQR(hash: String) : ByteArrayResource =
        shortUrlRepository.findByKey(hash)?.let {
            qrService.qrEncode(hash)
        } ?: throw QrUriNotFound(hash)
}
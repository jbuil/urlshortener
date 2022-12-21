import es.unizar.urlshortener.core.*

interface GenerateQRUseCase {
    fun generateQR(hash: String) : ByteArray
}

class GenerateQRUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService,
    private val qrService: QRService
) : GenerateQRUseCase {
    override fun generateQR(hash: String) : ByteArray =
        shortUrlRepository.findByKey(hash)?.let {
            qrService.qrEncode(hash)
        } ?: throw QrUriNotFound(hash)
}
package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import org.springframework.cache.*

interface RetrieveQRUseCase {
    fun retrieveQR(hash: String, cache: CacheManager): ByteArray
}

class RetrieveQRUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService
) : RetrieveQRUseCase {
    override fun retrieveQR(hash: String, cacheManager: CacheManager): ByteArray {
        val qrCache = cacheManager.getCache("qr-codes")
        val qrBytes = qrCache?.get(hash, ByteArray::class.java)
        if(qrBytes != null) {
            // Devuelve la imagen como una respuesta HTTP
            return qrBytes
        } else {
            // Imagen QR del hash no existe en la caché
            val shortURL = shortUrlRepository.findByKey(hash) ?: throw QrUriNotFound(hash)
            when (shortURL.properties.safe) {
                null -> throw UrlNotVerified(shortURL.redirection.target)
                false -> throw UrlNotSafe(shortURL.redirection.target)
                else -> throw QrUriNotFound(hash)
            }  // shortURL.qr == null, no se quería QR
        }
    }
}
package es.unizar.urlshortener.infrastructure.delivery

import GenerateQRUseCase
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.*
import kotlinx.coroutines.*
import org.springframework.cache.*
import org.springframework.cache.annotation.*
import org.springframework.hateoas.server.mvc.*
import org.springframework.http.*
import org.springframework.http.MediaType.*
import org.springframework.web.bind.annotation.*
import ru.chermenin.ua.*
import java.net.*
import javax.servlet.http.*


/**
 * The specification of the controller.
 */
interface UrlShortenerController {

    /**
     * Redirects and logs a short url identified by its [id].
     *
     * **Note**: Delivery of use cases [RedirectUseCase] and [LogClickUseCase].
     */
    fun redirectTo(id: String, request: HttpServletRequest): ResponseEntity<ClickOut>

    /**
     * Creates a short url from details provided in [data].
     *
     * **Note**: Delivery of use case [CreateShortUrlUseCase].
     */
    fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut>

    fun getQR(hash: String, request: HttpServletRequest) : ResponseEntity<ByteArray>


}

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    val url: String,
    val wantQR: String,
    val sponsor: String? = null,
)

/**
 * Data returned after the creation of a short url.
 */
data class ShortUrlDataOut(
    val url: URI? = null,
    val qr: URI? = null,
    val properties: Map<String, Any> = emptyMap(),
)


/**
 * The implementation of the controller.
 *
 * **Note**: Spring Boot is able to discover this [RestController] without further configuration.
 */
@RestController
class UrlShortenerControllerImpl(
    val redirectUseCase: RedirectUseCase,
    val logClickUseCase: LogClickUseCase,
    val createShortUrlUseCase: CreateShortUrlUseCase,
    val generateQRUseCase: GenerateQRUseCase,
    val shortUrlRepository: ShortUrlRepositoryService,
    val fileController: FileController,
    val cacheManager: CacheManager
) : UrlShortenerController {
    @GetMapping("/{id:(?!api|docs|index|openApi.yaml|upload).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<ClickOut> =
        redirectUseCase.redirectTo(id).let {
            var userAgent: UserAgent? = null
            val redirection = redirectUseCase.redirectTo(id)
            if(request.getHeader("user-agent") != null){
                userAgent = UserAgent.parse(request.getHeader("user-agent"))
            }

            logClickUseCase.logClick(id, ClickProperties(ip = request.remoteAddr ?: "0.0.0.0",
                platform = userAgent?.os?.toString() ,
                browser = userAgent?.browser?.toString(),referrer = redirection.target))

            val shortUrl = shortUrlRepository.findByKey(id)
            // Si el campo safe del objeto ShortUrl no tiene un valor, devolver una respuesta HTTP con código 503
            // y encabezado Retry-After configurado con el tiempo en el que se espera que el campo safe tenga un valor
            if (shortUrl != null) {
                if (shortUrl.properties.safe == null) {
                    throw UrlNotVerified(id)
                }
            }
            val h = HttpHeaders()
            shortUrl?.properties?.let { it1 -> println(it1.safe) }
            if (shortUrl != null) {
                if (shortUrl.properties.safe == false) {
                    throw UrlNotSafe(id)
                }
            }

            h.location = URI.create(it.target)

            val clickOut = ClickOut(
                hash = id,
                browser = userAgent?.browser?.toString(),
                platform = userAgent?.os?.toString()
            )
            ResponseEntity<ClickOut>(clickOut,h, HttpStatus.valueOf(it.mode))
        }
    @PostMapping("/api/link", consumes = [APPLICATION_FORM_URLENCODED_VALUE])
    override fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut> =
        createShortUrlUseCase.create(
            url = data.url,
            wantQR = data.wantQR == "Yes",
            data = ShortUrlProperties(
                ip = request.remoteAddr,
                sponsor = data.sponsor,
            )
        ).let {
            val h = HttpHeaders()
            val url = linkTo<UrlShortenerControllerImpl> { redirectTo(it.hash, request) }.toUri()
            h.location = url
            val response = ShortUrlDataOut(
                url = url,
                qr = it.qr?.let { it1 -> URI.create(it1) }
            )
            // Comprobar si campo safe ha sido actualizado por Rabbit
            runBlocking { checkSafe(data.wantQR, it) }
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
        }
    private suspend fun generateQR(hash: String) {
        val qrCache = cacheManager.getCache("qr-codes")
        qrCache?.put(hash, generateQRUseCase.generateQR(hash))
    }
    private suspend fun checkSafe(wantQR: String, su: ShortUrl) {
        var safe = su.properties.safe
        if(safe == null) safe = shortUrlRepository.findByKey(su.hash)?.properties?.safe
        while(safe == null) {
            safe = shortUrlRepository.findByKey(su.hash)?.properties?.safe
        }
        if((wantQR == "Yes") && (safe == true)) {
            generateQR(su.hash)
        }
    }
    @Cacheable("qr-codes")
    @GetMapping("/{hash}/qr")
    override fun getQR(@PathVariable hash: String, request: HttpServletRequest): ResponseEntity<ByteArray> {
        // Obtiene el código QR de la caché
        val qrCache = cacheManager.getCache("qr-codes")
        val qrBytes = qrCache?.get(hash, ByteArray::class.java)
        if(qrBytes != null) {
            // Devuelve la imagen como una respuesta HTTP
            val headers = HttpHeaders()
            headers.contentType = IMAGE_PNG
            return ResponseEntity<ByteArray>(qrBytes, headers, HttpStatus.OK)
        } else {
            // Imagen QR del hash no existe en la caché
            val shortURL = shortUrlRepository.findByKey(hash) ?: throw QrUriNotFound(hash)
            when (shortURL.properties.safe) {
                null -> throw UrlNotVerified(shortURL.redirection.target)
                false -> throw UrlNotSafe(shortURL.redirection.target)
                else -> throw QrUriNotFound(hash)}  // shortURL.qr == null
        }
    }
}

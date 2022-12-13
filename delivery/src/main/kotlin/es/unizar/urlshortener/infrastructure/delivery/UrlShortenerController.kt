package es.unizar.urlshortener.infrastructure.delivery

import GenerateQRUseCase
import com.google.common.net.HttpHeaders.*
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.*
import org.springframework.core.io.*
import org.springframework.hateoas.server.mvc.*
import org.springframework.http.*
import org.springframework.http.MediaType.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.*
import org.springframework.web.servlet.mvc.support.*
import ru.chermenin.ua.*
import java.net.*
import java.time.*
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
    suspend fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut>

    fun generateQR(hash: String, request: HttpServletRequest) : ResponseEntity<ByteArrayResource>


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
    val fileController: FileController

) : UrlShortenerController {

    @GetMapping("/{id:(?!api|index).*}")
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
                   // val error = "{\"error\": \"URI de destino no validada todavía\"}"
                    val headers = HttpHeaders()
                    val instant = Instant.ofEpochMilli(System.currentTimeMillis() + 50)
                    headers.set(RETRY_AFTER, instant.toString())
                   // val status = HttpStatus.SERVICE_UNAVAILABLE
                    //return ResponseEntity<Void>(headers, status)
                    throw UrlNotVerified(id)
                }
            }
            val h = HttpHeaders()
            shortUrl?.properties?.let { it1 -> println(it1.safe) }
            if (shortUrl != null) {
                if (shortUrl.properties.safe == false) {
                    //return ResponseEntity<Void>(h, HttpStatus.FORBIDDEN)
                    throw UrlNotSafe(id)
                }
            }

            h.location = URI.create(it.target)


            val clikOut = ClickOut(
                    hash = id,
                    browser = userAgent?.browser?.toString(),
                    platform = userAgent?.os?.toString()
                )
            ResponseEntity<ClickOut>(clikOut,h, HttpStatus.valueOf(it.mode))
        }

    @PostMapping("/api/link", consumes = [APPLICATION_FORM_URLENCODED_VALUE])
    override suspend fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut> =
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
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
        }

    @GetMapping("/{hash}/qr")
    override fun generateQR(@PathVariable hash: String, request: HttpServletRequest) : ResponseEntity<ByteArrayResource> =
        generateQRUseCase.generateQR(hash).let {
            val h = HttpHeaders()
            h.set(CONTENT_TYPE, IMAGE_PNG.toString())
            ResponseEntity<ByteArrayResource>(it, h, HttpStatus.OK)
        }
}

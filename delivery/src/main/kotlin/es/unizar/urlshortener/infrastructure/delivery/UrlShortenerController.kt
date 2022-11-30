package es.unizar.urlshortener.infrastructure.delivery

import GenerateQRUseCase
import com.google.common.net.HttpHeaders.*
import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.usecases.*
import org.springframework.core.io.*
import org.springframework.hateoas.server.mvc.*
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import ru.chermenin.ua.UserAgent
import java.net.*
import java.util.*
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
    fun redirectTo(id: String, request: HttpServletRequest): ResponseEntity<Void>

    /**
     * Creates a short url from details provided in [data].
     *
     * **Note**: Delivery of use case [CreateShortUrlUseCase].
     */
    fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut>

    fun generateQR(hash: String, request: HttpServletRequest) : ResponseEntity<ByteArrayResource>

}

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    val url: String,
    val wantQR: Boolean?,
    val sponsor: String? = null
)

/**
 * Data returned after the creation of a short url.
 */
data class ShortUrlDataOut(
    val url: URI? = null,
    val qr: URI? = null,
    val properties: Map<String, Any> = emptyMap()
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
    val generateQRUseCase: GenerateQRUseCase
) : UrlShortenerController {

    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Void> =
        redirectUseCase.redirectTo(id).let {
            val userAgent =  UserAgent.parse(request.getHeader("user-agent"))
            logClickUseCase.logClick(id, ClickProperties(ip = request.remoteAddr,platform = userAgent.os.toString() ,browser = userAgent.browser.toString()))
            val h = HttpHeaders()
            h.location = URI.create(it.target)
            ResponseEntity<Void>(h, HttpStatus.valueOf(it.mode))
        }


    @PostMapping("/api/link", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut> =
        createShortUrlUseCase.create(
            url = data.url,
            data = ShortUrlProperties(
                ip = request.remoteAddr,
                sponsor = data.sponsor
            )
        ).let {
            val h = HttpHeaders()
            val url = linkTo<UrlShortenerControllerImpl> { redirectTo(it.hash, request) }.toUri()
            h.location = url
            val qr = assignQR(data.wantQR, url.path)
            val response = ShortUrlDataOut(
                url = url,
                qr = qr,
                properties = mapOf(
                    "safe" to it.properties.safe
                )
            )
            ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
        }

    @GetMapping("/{hash}/qr")
    override fun generateQR(@PathVariable hash: String, request: HttpServletRequest) : ResponseEntity<ByteArrayResource> =

        generateQRUseCase.generateQR(hash).let {
            val h = HttpHeaders()
            h.set(CONTENT_TYPE, "image/png")
            print(hash)
            ResponseEntity<ByteArrayResource>(it, h, HttpStatus.OK)
        }

    fun assignQR(wantQR: Boolean?, path: String): URI? = when (wantQR) {
        true -> URI.create(baseURI + path + qrEndpoint)
        else -> null
    }
    fun getClientOS(request: HttpServletRequest): String? {
        val browserDetails: String = request.getHeader("User-Agent")


        val lowerCaseBrowser = browserDetails.lowercase(Locale.getDefault())
        print(lowerCaseBrowser)
        return if (lowerCaseBrowser.contains("windows")) {
            "Windows"
        } else if (lowerCaseBrowser.contains("mac")) {
            "Mac"
        } else if (lowerCaseBrowser.contains("x11")) {
            "Unix"
        } else if (lowerCaseBrowser.contains("android")) {
            "Android"
        } else if (lowerCaseBrowser.contains("iphone")) {
            "IPhone"
        } else if (lowerCaseBrowser.contains("curl")) {
            "curl"
        } else {
            "UnKnown, More-Info: $browserDetails"
        }
    }
    fun getClientBrowser(request: HttpServletRequest): String? {
        val browserDetails = request.getHeader("User-Agent")
        val user = browserDetails.lowercase(Locale.getDefault())
        var browser = ""

        //===============Browser===========================
        if (user.contains("msie")) {
            val substring =
                browserDetails.substring(browserDetails.indexOf("MSIE")).split(";".toRegex()).toTypedArray()[0]
            browser = substring.split(" ".toRegex()).toTypedArray()[0].replace(
                "MSIE",
                "IE"
            ) + "-" + substring.split(" ".toRegex()).toTypedArray()[1]
        } else if (user.contains("safari") && user.contains("version")) {
            browser =
                browserDetails.substring(browserDetails.indexOf("Safari")).split(" ".toRegex()).toTypedArray()[0].split(
                    "/".toRegex()
                ).toTypedArray()[0] + "-" + browserDetails.substring(
                    browserDetails.indexOf("Version")
                ).split(" ".toRegex()).toTypedArray()[0].split("/".toRegex()).toTypedArray()[1]
        } else if (user.contains("opr") || user.contains("opera")) {
            if (user.contains("opera")) browser =
                browserDetails.substring(browserDetails.indexOf("Opera")).split(" ".toRegex()).toTypedArray()[0].split(
                    "/".toRegex()
                ).toTypedArray()[0] + "-" + browserDetails.substring(
                    browserDetails.indexOf("Version")
                ).split(" ".toRegex()).toTypedArray()[0].split("/".toRegex())
                    .toTypedArray()[1] else if (user.contains("opr")) browser =
                browserDetails.substring(browserDetails.indexOf("OPR")).split(" ".toRegex()).toTypedArray()[0].replace(
                    "/",
                    "-"
                ).replace(
                    "OPR", "Opera"
                )
        } else if (user.contains("chrome")) {
            browser = browserDetails.substring(browserDetails.indexOf("Chrome")).split(" ".toRegex())
                .toTypedArray()[0].replace("/", "-")
        } else if (user.indexOf("mozilla/7.0") > -1 || user.indexOf("netscape6") != -1 || user.indexOf(
                "mozilla/4.7"
            ) != -1 || user.indexOf("mozilla/4.78") != -1 || user.indexOf(
                "mozilla/4.08"
            ) != -1 || user.indexOf("mozilla/3") != -1
        ) {
            //browser=(userAgent.substring(userAgent.indexOf("MSIE")).split(" ")[0]).replace("/", "-");
            browser = "Netscape-?"
        } else if (user.contains("firefox")) {
            browser = browserDetails.substring(browserDetails.indexOf("Firefox")).split(" ".toRegex())
                .toTypedArray()[0].replace("/", "-")
        } else if (user.contains("rv")) {
            browser = "IE"
        } else {
            browser = "UnKnown, More-Info: $browserDetails"
        }
        return browser
    }

    companion object {
        const val baseURI = "http://localhost"
        const val qrEndpoint  = "/qr"
    }
}

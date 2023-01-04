package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.UrlNotSafe
import es.unizar.urlshortener.core.UrlNotVerified
import es.unizar.urlshortener.core.usecases.InfoHTTPHeaderUseCase
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import javax.servlet.http.HttpServletRequest


data class ClickOut(
    var hash: String,
    val browser: String? = ClickProperties().browser,
    val platform: String? = ClickProperties().platform,
    val created: OffsetDateTime = OffsetDateTime.now()
)

data class InfoHTTPHeaderOut(
    var info: ArrayList<ClickOut>? = null
)
/**
 * The specification of the controller.
 */
interface InfoHTPPHeaderController {

    /**
     * Devuelve información relevante sobre la URI acortada identificada por el parámetro id..
     *
     * **Note**:
     */
    fun getInfo(id: String,request: HttpServletRequest): ResponseEntity<ArrayList<ClickOut>>
}

/**
 * The implementation of the controller.
 *
 * **Note**: Spring Boot is able to discover this [RestController] without further configuration.
 */
@RestController
class InfoHTTPHeaderControllerImpl (
    val infoHTTPHeaderUseCase: InfoHTTPHeaderUseCase
) : InfoHTPPHeaderController {


    fun verifyUrlSafety(url: ShortUrl?, id: String) {
        if (url != null) {
            if(url.properties.safe == null){
                throw UrlNotVerified(id)
            } else {
                if( !url.properties.safe!!){
                    throw UrlNotSafe(id)
                }
            }
        }
    }

    @GetMapping("/api/link/{id}")
    override fun getInfo(@PathVariable id: String,request: HttpServletRequest): ResponseEntity<ArrayList<ClickOut>> {
        return runBlocking {
            val url: ShortUrl? = infoHTTPHeaderUseCase.getInfoUrl(id)
            if (url != null) {
                verifyUrlSafety(url,id)
            }
            val let = infoHTTPHeaderUseCase.getInfo(id).let {
                var response: ArrayList<ClickOut> = ArrayList<ClickOut>()
                if (it != null) {
                    for (i in it) {
                        response.add(
                            ClickOut(
                                hash = i.hash,
                                browser = i.properties.browser,
                                platform = i.properties.platform,
                                created = i.created
                            )
                        )
                    }
                }

                ResponseEntity<ArrayList<ClickOut>>(response, HttpStatus.OK)
            }
            return@runBlocking let
        }
    }


}







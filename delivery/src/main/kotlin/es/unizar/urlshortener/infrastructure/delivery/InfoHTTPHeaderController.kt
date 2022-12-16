package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.usecases.InfoHTTPHeaderUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.chermenin.ua.UserAgent
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
    fun getUserAgent( id: String, request: HttpServletRequest): ResponseEntity<ClickOut>
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




    @GetMapping("/api/link/{id}")
    override fun getInfo(@PathVariable id: String,request: HttpServletRequest): ResponseEntity<ArrayList<ClickOut>> {
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
        return let
    }
    override fun getUserAgent(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<ClickOut> {
        // Busca el objeto ClickOut con el ID especificado
        val click = infoHTTPHeaderUseCase.getInfo(id)?.firstOrNull()
        val clickOut = click?.let {
            ClickOut(
                hash = it.hash,
                browser = click.properties.browser,
                platform = click.properties.platform,
                created = click.created
            )
        }


        // Si se encuentra el objeto, asigna el valor de la cabecera User-Agent a la propiedad userAgent del objeto
        // Devuelve el objeto ClickOut
        return ResponseEntity.ok(clickOut)
    }




}


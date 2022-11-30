package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.InfoHTTPHeader
import es.unizar.urlshortener.core.usecases.InfoHTTPHeaderUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

data class InfoHTTPHeaderOut(
    val info: List<Click>?
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
    fun getInfo(id: String,request: HttpServletRequest): ResponseEntity<InfoHTTPHeaderOut>

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
    override fun getInfo(@PathVariable id: String,request: HttpServletRequest): ResponseEntity<InfoHTTPHeaderOut> =
        infoHTTPHeaderUseCase.getInfo(id).let{
            val response = InfoHTTPHeaderOut(
                info = it
            )
            ResponseEntity<InfoHTTPHeaderOut>(response,HttpStatus.OK)
        }




}


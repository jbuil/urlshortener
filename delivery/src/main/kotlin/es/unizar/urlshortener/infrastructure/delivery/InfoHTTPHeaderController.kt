package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.usecases.InfoHTTPHeaderUserCase
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

/**
 * The specification of the controller.
 */
interface InfoHTPPHeaderController {

    /**
     * Devuelve información relevante sobre la URI acortada identificada por el parámetro id..
     *
     * **Note**:
     */
    fun infoner(id: String, request: HttpServletRequest): String

}

/**
 * The implementation of the controller.
 *
 * **Note**: Spring Boot is able to discover this [RestController] without further configuration.
 */
@Controller
class InfoHTTPHeaderControllerImpl (
    val infoHTTPHeaderUserCase: InfoHTTPHeaderUserCase
) : InfoHTPPHeaderController {

    @RequestMapping("/api/link/{id}")
    override fun infoner(@PathVariable id: String, request: HttpServletRequest): String {
        val info = InfoHTTPHeaderUserCase.getInfo(id)
        if (info != null) {
            for (i in info) {
                println(i.date + ", " + i.browser + ", " + i.platform)
            }
        }
        return "info"
    }
}
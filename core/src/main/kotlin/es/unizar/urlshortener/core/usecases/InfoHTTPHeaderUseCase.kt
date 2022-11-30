package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import org.springframework.core.io.ByteArrayResource
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle;

/**
 * Given a key returns a [Redirection] that contains a [URI target][Redirection.target]
 * and an [HTTP redirection mode][Redirection.mode].
 *
 * **Note**: This is an example of functionality.
 */
interface InfoHTTPHeaderUseCase {
    fun getInfo(key: String): List<Click>?
}

/**
 * Implementation of [InfoClientUserCase].
 */
class InfoHTTPHeaderCaseImpl(
    private val clickRepository: ClickRepositoryService
) : InfoHTTPHeaderUseCase {
    override fun getInfo(key: String): List<Click> {
        return clickRepository.getInfo(key)
    }
}
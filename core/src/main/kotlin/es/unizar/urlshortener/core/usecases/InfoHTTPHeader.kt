package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle;

/**
 * Given a key returns a [Redirection] that contains a [URI target][Redirection.target]
 * and an [HTTP redirection mode][Redirection.mode].
 *
 * **Note**: This is an example of functionality.
 */
interface InfoHTTPHeaderUserCase {
    fun getInfo(key: String): ArrayList<InfoHTTPHeader>?
}

/**
 * Implementation of [InfoClientUserCase].
 */
class InfoClientUserCaseImpl(
    private val clickRepository: ClickRepositoryService
) : InfoHTTPHeaderUserCase {
    override fun getInfo(key: String): ArrayList<InfoHTTPHeader>? {
        if(clickRepository.existHash(key)){
            var list = ArrayList<InfoHTTPHeader>()
            val fmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            for(e in clickRepository.findByHash(key)){
                list.add(InfoHTTPHeader(fmt.format(e.created), e.properties.browser, e.properties.platform))
            }
            return list
        } else {
            return null
        }
    }
}
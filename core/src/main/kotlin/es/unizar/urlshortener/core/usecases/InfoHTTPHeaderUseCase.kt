package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*


/**
 * Given a key returns a [Redirection] that contains a [URI target][Redirection.target]
 * and an [HTTP redirection mode][Redirection.mode].
 *
 * **Note**: This is an example of functionality.
 */
interface InfoHTTPHeaderUseCase {
    fun getInfo(key: String): List<Click>?
    fun getInfoUrl(key: String): ShortUrl?
}

/**
 * Implementation of [InfoClientUserCase].
 */
class InfoHTTPHeaderCaseImpl(
    private val clickRepository: ClickRepositoryService,
    private val shortUrlRepositoryService: ShortUrlRepositoryService
) : InfoHTTPHeaderUseCase {
    override fun getInfo(key: String): List<Click> {
        return clickRepository.getInfo(key)
    }

    override fun getInfoUrl(key: String): ShortUrl? {
        return shortUrlRepositoryService.findByKey(key)
    }

}
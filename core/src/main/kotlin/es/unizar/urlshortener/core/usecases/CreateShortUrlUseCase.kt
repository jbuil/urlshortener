package es.unizar.urlshortener.core.usecases
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import es.unizar.urlshortener.core.*

/**
 * Given an url returns the key that is used to create a short URL.
 * When the url is created optional data may be added.
 *
 * **Note**: This is an example of functionality.
 */
interface CreateShortUrlUseCase {
    fun create(url: String, data: ShortUrlProperties): ShortUrl
}

/**
 * Implementation of [CreateShortUrlUseCase].
 */
class CreateShortUrlUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService,
    private val validatorService: ValidatorService,
    private val hashService: HashService,
    private val rabbitMQService: RabbitMQService
) : CreateShortUrlUseCase {
    override fun create(url: String, data: ShortUrlProperties): ShortUrl {
         return runBlocking {
            val deferredShortUrl = async {
                // Code to shorten the URL goes here
                if (validatorService.isValid(url)) {
                    val id: String = hashService.hasUrl(url)
                    val su = ShortUrl(
                        hash = id,
                        redirection = Redirection(target = url),
                        properties = ShortUrlProperties(
                            safe = data.safe,
                            ip = data.ip,
                            sponsor = data.sponsor
                        )
                    )
                    rabbitMQService.write(url, id)
                    shortUrlRepository.save(su)
                   // validatorService.sendMessage(url, id)
                } else {
                    throw InvalidUrlException(url)
                }
            }

            // Return the shortened URL
            return@runBlocking deferredShortUrl.await()
        }
    }

}

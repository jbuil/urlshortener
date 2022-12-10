package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*

/**
 * Given an url returns the key that is used to create a short URL.
 * When the url is created optional data may be added.
 *
 * **Note**: This is an example of functionality.
 */
interface CreateShortUrlUseCase {
    suspend fun create(url: String, data: ShortUrlProperties): ShortUrl
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
    override suspend fun create(url: String, data: ShortUrlProperties): ShortUrl {
        // Code to shorten the URL goes here
        if (!validatorService.isValid(url)) {
            throw InvalidUrlException(url)
        }
        val id: String = hashService.hasUrl(url)
        val su = ShortUrl(
            hash = id,
            redirection = Redirection(target = url),
            properties = ShortUrlProperties(
                ip = data.ip,
                sponsor = data.sponsor
            )
        )
        rabbitMQService.write(url, id)
        shortUrlRepository.save(su)

        // Return the shortened URL
        return su
    }
}


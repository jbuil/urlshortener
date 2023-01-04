package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import org.springframework.beans.factory.annotation.*

/**
 * Given an url returns the key that is used to create a short URL.
 * When the url is created optional data may be added.
 *
 * **Note**: This is an example of functionality.
 */
interface CreateShortUrlUseCase {
     fun create(url: String, wantQR: Boolean, data: ShortUrlProperties): ShortUrl
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
    @Value("\${baseURI}")
    lateinit var baseURI: String
    @Value("\${qrEndpoint}")
    lateinit var qrEndpoint: String
    override fun create(url: String, wantQR: Boolean, data: ShortUrlProperties): ShortUrl {
        if (!validatorService.isValid(url)) {
            throw InvalidUrlException(url)
        }
        val id: String = hashService.hasUrl(url)
        val qr: String? = if (wantQR) assignQR(id) else null
        val su = ShortUrl(
            hash = id,
            redirection = Redirection(target = url),
            qr = qr,
            properties = ShortUrlProperties(
                ip = data.ip,
                sponsor = data.sponsor
            )
        )
        shortUrlRepository.save(su)
        rabbitMQService.write(url, id)

        // Return the shortened URL
        return su
    }
    private fun assignQR(hash: String): String = baseURI + hash + qrEndpoint
}

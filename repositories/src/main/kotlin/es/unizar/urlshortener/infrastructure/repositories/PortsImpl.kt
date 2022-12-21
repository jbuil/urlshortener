package es.unizar.urlshortener.infrastructure.repositories

import es.unizar.urlshortener.core.*

/**
 * Implementation of the port [ClickRepositoryService].
 */
class ClickRepositoryServiceImpl(
    private val clickEntityRepository: ClickEntityRepository
) : ClickRepositoryService {
    override fun save(cl: Click): Click = clickEntityRepository.save(cl.toEntity()).toDomain()

    //override fun findByHash(id: String): List<Click> = clickEntityRepository.findAllByHash(id).map { it.toDomain() }

   // override fun existHash(id: String): Boolean = clickEntityRepository.existsByHash(id)

    override fun getInfo(id: String): List<Click> {
        val clicks = clickEntityRepository.findByHash(id)
        val clickList = mutableListOf<Click>()
        for (click in clicks) {
            clickList.add(click.toDomain())
        }
        return clickList
    }
}

/**
 * Implementation of the port [ShortUrlRepositoryService].
 */
class ShortUrlRepositoryServiceImpl(
    private val shortUrlEntityRepository: ShortUrlEntityRepository
) : ShortUrlRepositoryService {
    override fun findByKey(id: String): ShortUrl? = shortUrlEntityRepository.findByHash(id)?.toDomain()

    override fun save(su: ShortUrl): ShortUrl = shortUrlEntityRepository.save(su.toEntity()).toDomain()

    override  fun updateSafe(hash: String, safe: Boolean) {
        val shortUrlEntity = shortUrlEntityRepository.findByHash(hash)
        shortUrlEntity?.let {
            it.safe = safe
            shortUrlEntityRepository.save(it)
        }
    }
}


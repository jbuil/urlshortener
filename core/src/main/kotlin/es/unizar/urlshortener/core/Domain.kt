package es.unizar.urlshortener.core

import java.time.OffsetDateTime

/**
 *  A [InfoHTTPHeader] is the bag of properties that a [infoHTTPHeaderUseCase] may have.
 */
data class InfoHTTPHeader(
    val hash: String,
    val browser: String? = ClickProperties().browser,
    val platform: String? = ClickProperties().platform,
    val created: OffsetDateTime = OffsetDateTime.now()
)


/**
 * A [Click] captures a request of redirection of a [ShortUrl] identified by its [hash].
 */
data class Click(
    val hash: String,
    val properties: ClickProperties = ClickProperties(),
    val created: OffsetDateTime = OffsetDateTime.now()
)

/**
 * A [ShortUrl] is the mapping between a remote url identified by [redirection] and a local short url identified by [hash].
 */
data class ShortUrl(
    val hash: String,
    val redirection: Redirection,
    val created: OffsetDateTime = OffsetDateTime.now(),
    val properties: ShortUrlProperties = ShortUrlProperties()
)

/**
 * A [Redirection] specifies the [target] and the [status code][mode] of a redirection.
 * By default, the [status code][mode] is 307 TEMPORARY REDIRECT.
 */
data class Redirection(
    val target: String,
    val mode: Int = 307
)

/**
 * A [ShortUrlProperties] is the bag of properties that a [ShortUrl] may have.
 */
data class ShortUrlProperties(
    val ip: String? = null,
    val sponsor: String? = null,
    val safe: Boolean = true,
    val owner: String? = null,
    val country: String? = null
)

/**
 * A [ClickProperties] is the bag of properties that a [Click] may have.
 */
data class ClickProperties(
    val ip: String? = null,
    val referrer: String? = null,
    val browser: String? = null,
    val platform: String? = null,
    val country: String? = null){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClickProperties

        if (ip != other.ip) return false
        if (referrer != other.referrer) return false
        if (browser != other.browser) return false
        if (platform != other.platform) return false
        if (country != other.country) return false

        return true
    }
}


package es.unizar.urlshortener.core

import org.springframework.core.io.*

/**
 * [ClickRepositoryService] is the port to the repository that provides persistence to [Clicks][Click].
 */
interface ClickRepositoryService {
    fun save(cl: Click): Click
    //fun existHash(id: String): Boolean
   // fun findByHash(id: String): List<Click>
    fun getInfo(id: String): List<Click>
}

/**
 * [ShortUrlRepositoryService] is the port to the repository that provides management to [ShortUrl][ShortUrl].
 */
interface ShortUrlRepositoryService {
    fun findByKey(id: String): ShortUrl?
    fun save(su: ShortUrl): ShortUrl
}



/**
 * [HashService] is the port to the service that creates a hash from a URL.
 *
 * **Note**: It is a design decision to create this port. It could be part of the core .
 */
interface HashService {
    fun hasUrl(url: String): String
}

/**
 * [QRService] is the port to the service that creates a QR code from a shortened URI.
 *
 * **Note**: It is a design decision to create this port. It could be part of the core .
 */
interface QRService {
    fun qrEncode(hash: String) : ByteArrayResource
}
/**
 * [ValidatorService] is the port to the service that validates if an url can be shortened.
 *
 * **Note**: It is a design decision to create this port. It could be part of the core .
 */
interface ValidatorService {
    fun isValid(url: String): Boolean

    //fun isReachable(url: String): Boolean

    //fun isSecure(url: String): Boolean

    //fun sendMessage(url: String, hash: String)
}
/**
 * [RabbitMQService] is the port to the service that validates if an url can be shortened.
 *
 * **Note**: It is a design decision to create this port. It could be part of the core .
 */
interface RabbitMQService{
    fun read(): String
    fun write(url: String, id: String)
}/**
 * [GoogleSafeBrowsingService] is the port to the service that validates if an url can be shortened.
 *
 * **Note**: It is a design decision to create this port. It could be part of the core .
 */
interface GoogleSafeBrowsingService{
    fun isSafe(url: String): Boolean
}


package es.unizar.urlshortener.core



class InvalidUrlException(val url: String) : Exception("[$url] does not follow a supported schema")
class UrlNotSafe(val url: String) : Exception("[$url] is not safe")

class RedirectionNotFound(val key: String) : Exception("[$key] is not known")

class QrUriNotFound(val hash: String) : Exception("Destination URI [$hash] doesn't exist")

class UrlNotVerified(val url: String) : Exception("[$url] is not verified yet")
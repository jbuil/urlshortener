package es.unizar.urlshortener

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.*

/**
 * The marker that makes this project a Spring Boot application.
 */
@SpringBootApplication
@EnableCaching
class UrlShortenerApplication

/**
 * The main entry point.
 */
fun main(args: Array<String>) {
    runApplication<UrlShortenerApplication>(*args)
}

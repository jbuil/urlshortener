package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter


@RestControllerAdvice
class RestResponseEntityExceptionHandler : ResponseEntityExceptionHandler() {

    @ResponseBody
    @ExceptionHandler(value = [InvalidUrlException::class])
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    protected fun invalidUrls(ex: InvalidUrlException) = ErrorMessage(HttpStatus.BAD_REQUEST.value(), ex.message)


    @ResponseBody
    @ExceptionHandler(value = [RedirectionNotFound::class])
    @ResponseStatus(HttpStatus.NOT_FOUND)
    protected fun redirectionNotFound(ex: RedirectionNotFound) = ErrorMessage(HttpStatus.NOT_FOUND.value(), ex.message)

    @ResponseBody
    @ExceptionHandler(value = [QrUriNotFound::class])
    @ResponseStatus(HttpStatus.NOT_FOUND)
    protected fun qrUriNotFound(ex: QrUriNotFound) = ErrorMessage(HttpStatus.NOT_FOUND.value(), ex.message)

    @ResponseBody
    @ExceptionHandler(value = [UrlNotVerified::class])
    protected fun UrlNotVerified(ex: UrlNotVerified) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .header("Retry-after", "10000")
    .body(ErrorMessage(HttpStatus.BAD_REQUEST.value(), ex.message))

    @ResponseBody
    @ExceptionHandler(value = [UrlNotSafe::class])
    @ResponseStatus(HttpStatus.FORBIDDEN)
    protected fun UrlNotSafe(ex: UrlNotSafe) = ErrorMessage(HttpStatus.FORBIDDEN.value(), ex.message)




}

data class ErrorMessage(
    val statusCode: Int,
    val message: String?,
    val timestamp: String = DateTimeFormatter.ISO_DATE_TIME.format(OffsetDateTime.now())
)
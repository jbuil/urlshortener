package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.*
import javax.servlet.http.HttpServletRequest
import kotlin.collections.List


interface FileController {
  // fun index(): String
  suspend fun uploadFile(@RequestParam("file") file: MultipartFile, attributes: RedirectAttributes ): ResponseEntity<List<String>>
    fun status(): String
    fun upload(request: HttpServletRequest) : String
    fun download():String
}

@Controller
public class FileControllerImpl (
        val uploadFileService: UploadFileService
) : FileController {


    @GetMapping("/upload")
    override  fun upload( request: HttpServletRequest) : String {
        return "upload"
    }

    @PostMapping("/api/bulk")
    override suspend fun uploadFile(@RequestParam("file") file: MultipartFile,
                                    attributes: RedirectAttributes ): ResponseEntity<List<String>> =
        uploadFileService.saveFile(
            file
        ).let {
            println("entra en /api/bulk")
            var h = HttpHeaders()
            h.location = URI.create(it[0])
            h.contentType = MediaType("text" , "csv")
            ResponseEntity<List<String>>(it, h, HttpStatus.CREATED)
        }



    @GetMapping("/status")
    override fun status(): String {
        return "status"
    }

    @GetMapping("/download")
    override fun download(): String {
        return "download"
    }
}

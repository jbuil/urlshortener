package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.*
import javax.servlet.http.HttpServletRequest


interface FileController {
    fun uploadFile(@RequestParam("file") file: MultipartFile, attributes: RedirectAttributes ): ResponseEntity<ByteArray>
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
    override  fun uploadFile(@RequestParam("file") file: MultipartFile,
                                    attributes: RedirectAttributes ): ResponseEntity<ByteArray> {

        val csv = uploadFileService.saveFile(file)
        val headers = HttpHeaders()
        headers.contentType = MediaType("text", "csv")
        if(csv.contentEquals(ByteArray(0))){
             return ResponseEntity(csv, headers, HttpStatus.OK)
        }
        return ResponseEntity(csv, headers, HttpStatus.CREATED)
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

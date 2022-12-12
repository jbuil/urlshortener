package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import java.lang.StringBuilder
import java.util.*
import java.io.File
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import javax.servlet.http.HttpServletRequest

interface FileController {
  // fun index(): String
    fun uploadFile(@RequestParam("file") file: MultipartFile, attributes: RedirectAttributes ): String
    fun status(): String
    fun upload(request: HttpServletRequest) : String
}

@Controller
public class FileControllerImpl (
        val uploadFileService: UploadFileService
) : FileController {


    @GetMapping("/upload")
    override  fun upload( request: HttpServletRequest) : String {
        return "upload"
    }

    @PostMapping("/upload")
    override fun uploadFile( @RequestParam("file") file: MultipartFile, attributes: RedirectAttributes ): String {
        if (file.isEmpty) {
            attributes.addFlashAttribute("message", "Please, select a file")
            return "redirect:status"
        }
        /** TODO: Comprobar si el contenido del arhivo es valido */


        /** tratamiento del archivo */
        try {
            uploadFileService.saveFile(file)
            attributes.addFlashAttribute("message", "File loaded successfully")

        } catch (e: IOException) {
            attributes.addFlashAttribute("message", "File failed to load")
            return "redirect:status"
        }
        return "redirect:/status"
    }

    @GetMapping("/status")
    override fun status(): String {
        return "status"
    }
}

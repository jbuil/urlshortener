package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.io.File
import java.io.IOException
import java.util.*
import javax.servlet.http.HttpServletRequest


interface FileController {
  // fun index(): String
  suspend fun uploadFile(@RequestParam("file") file: MultipartFile, attributes: RedirectAttributes ): String
    fun status(): String
    fun upload(request: HttpServletRequest) : String
    fun download():String
}

@RestController
public class FileControllerImpl (
        val uploadFileService: UploadFileService
) : FileController {


    @GetMapping("/upload")
    override  fun upload( request: HttpServletRequest) : String {
        return "upload"
    }

    @PostMapping("/upload")
    override suspend fun uploadFile(@RequestParam("file") file: MultipartFile, attributes: RedirectAttributes ): String {
        if (file.isEmpty) {
            attributes.addFlashAttribute("message", "Please, select a file")
            return "redirect:status"
        }
        /** TODO: Comprobar si el contenido del arhivo es valido */


        /** tratamiento del archivo */
        try {
            val result = uploadFileService.saveFile(file)
            attributes.addFlashAttribute("message", "File loaded successfully")
            attributes.addFlashAttribute("result", result)

        } catch (e: IOException) {
            attributes.addFlashAttribute("message", "File failed to load")
            return "redirect:status"
        }
        return "redirect:/download"
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

package es.unizar.urlshortener.infrastructure.delivery


import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import org.springframework.web.bind.annotation.PostMapping
import java.lang.StringBuilder
import java.util.*
import java.io.File
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

interface FileController {
   fun index(): String
    fun uploadFile(@RequestParam("file") file: MultipartFile, attributes: RedirectAttributes ): String
    fun status(): String
}

@Controller
public class FileControllerImpl (
        val createShortUrlUseCase : CreateShortUrlUseCase
) : FileController {
    @GetMapping("/")
    override fun index(): String{
        return "upload_URLs_File";
    }

    @PostMapping("/upload_URLs_File")
    override fun uploadFile(@RequestParam("file") file: MultipartFile, attributes: RedirectAttributes ): String {
        if (file.isEmpty) {
            attributes.addFlashAttribute("message", "Please, select a file")
            return "redirect:status"
        }
        /** TODO: Comprobar si el contenido del arhivo es valido */

        val builder = StringBuilder()
        builder.append("delivery")
        builder.append(File.separator)
        builder.append(file.getOriginalFilename())

        /** tratamiento del archivo */
        try {
            val reader = BufferedReader(file.originalFilename?.let { FileReader(it) })
            val writer = BufferedWriter(FileWriter("SHORTED.txt"))
            var line: String?
            line = reader.readLine()

            while (line != null) {
                //val content = String
                /** TODO: convert line into a shortURL */
                writer.write(line)
                writer.newLine()
                line = readLine()
            }

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

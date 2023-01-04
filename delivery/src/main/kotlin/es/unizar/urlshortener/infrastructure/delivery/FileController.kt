package es.unizar.urlshortener.infrastructure.delivery
import es.unizar.urlshortener.core.WebSocketService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.FileSystemResource
import org.springframework.hateoas.server.mvc.linkTo

import es.unizar.urlshortener.core.*
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.*
import javax.servlet.http.HttpServletRequest


interface FileController {
    fun uploadFile(@RequestParam("file") file: MultipartFile,clientId: String): ResponseEntity<ByteArray>
    fun status(): String
    fun upload(request: HttpServletRequest) : String
    fun download():String
}

@RestController
class FileControllerImpl (
        val uploadFileService: UploadFileService,
        @Autowired val webSocketService: WebSocketService
) : FileController {


    @GetMapping("/upload")
    override  fun upload( request: HttpServletRequest) : String {
        return "upload"
    }
    @MessageMapping("/sendMessage")
    @SendToUser("/queue/specific-user")
    fun sendMessage(session: WebSocketSession, message: String): String {
    // Enviar mensaje a través de la sesión de WebSocket del usuario específico
        session.sendMessage(TextMessage(message))
        return message
    }


    val progressMap: MutableMap<String, Int> = mutableMapOf()
    @PostMapping("/api/bulk")
    override fun uploadFile(@RequestParam("file") file: MultipartFile, clientId: String): ResponseEntity<ByteArray> {
        // Crear una nueva sesión de WebSocket para el cliente especificado
        val session = webSocketService.createSession("clientId")

        // Enviar un mensaje de inicio a través de la sesión de WebSocket
        session.sendMessage(TextMessage("Iniciando procesamiento del archivo del cliente: $clientId"))

        val csv = uploadFileService.saveFile(file) { progress ->
            // Enviar un mensaje de progreso a través de la sesión de WebSocket
            session.sendMessage(TextMessage("Progreso de $progress% del cliente: $clientId"))
        }
        // Enviar un mensaje de fin a través de la sesión de WebSocket
        session.sendMessage(TextMessage("Procesamiento del archivo completado del cliente: $clientId"))

        // Cerrar la sesión de WebSocket


        val headers = HttpHeaders()
        headers.contentType = MediaType("text", "csv")
        if(csv.contentEquals(ByteArray(0))) {
            return ResponseEntity(csv, headers, HttpStatus.OK)
        }
        return ResponseEntity(csv, headers, HttpStatus.OK)
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

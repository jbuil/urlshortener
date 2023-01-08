package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.socket.TextMessage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.util.*


interface FileController {
    fun uploadFile(@RequestParam("file") file: MultipartFile,clientId: String): ResponseEntity<ByteArray>
}

@RestController
class FileControllerImpl (
        val uploadFileService: UploadFileService,
        @Autowired val webSocketService: WebSocketService
) : FileController {


    val progressMap: MutableMap<String, Int> = mutableMapOf()
    @PostMapping("/api/bulk")
    override fun uploadFile(@RequestParam("file") file: MultipartFile, clientId: String): ResponseEntity<ByteArray> {
        // Crear una nueva sesión de WebSocket para el cliente especificado
        val session = webSocketService.createSession()

        // Enviar un mensaje de inicio a través de la sesión de WebSocket
        session.sendMessage(TextMessage("Iniciando procesamiento del archivo del cliente: $clientId"))

        val csv = uploadFileService.saveFile(file) { progress ->
            // Enviar un mensaje de progreso a través de la sesión de WebSocket
            session.sendMessage(TextMessage("Progreso de $progress% del cliente: $clientId"))
        }
        // Enviar un mensaje de fin a través de la sesión de WebSocket
        session.sendMessage(TextMessage("Procesamiento del archivo completado del cliente: $clientId"))

        val headers = HttpHeaders()
        headers.contentType = MediaType("text", "csv")
        val inputStream = file.inputStream
        val reader = BufferedReader(InputStreamReader(inputStream))
        headers.location = URI.create(reader.readLine().split(',')[0])
        if(csv.contentEquals(ByteArray(0))) {
            return ResponseEntity(csv, headers, HttpStatus.CREATED)
        }
        return ResponseEntity(csv, headers, HttpStatus.CREATED)
    }
}

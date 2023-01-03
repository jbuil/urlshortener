package es.unizar.urlshortener

import GenerateQRUseCaseImpl
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCaseImpl
import es.unizar.urlshortener.core.usecases.InfoHTTPHeaderCaseImpl
import es.unizar.urlshortener.core.usecases.LogClickUseCaseImpl
import es.unizar.urlshortener.core.usecases.RedirectUseCaseImpl
import es.unizar.urlshortener.infrastructure.delivery.*
import es.unizar.urlshortener.infrastructure.repositories.ClickEntityRepository
import es.unizar.urlshortener.infrastructure.repositories.ClickRepositoryServiceImpl
import es.unizar.urlshortener.infrastructure.repositories.ShortUrlEntityRepository
import es.unizar.urlshortener.infrastructure.repositories.ShortUrlRepositoryServiceImpl
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.config.annotation.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.*


/**
 * Wires use cases with service implementations, and services implementations with repositories.
 *
 * **Note**: Spring Boot is able to discover this [Configuration] without further configuration.
 */
@EnableWebSocket
@Configuration
class ApplicationConfiguration(
    @Autowired val shortUrlEntityRepository: ShortUrlEntityRepository,
    @Autowired val clickEntityRepository: ClickEntityRepository,
    @Autowired val rabbitTemplate: RabbitTemplate,

    ) {




    @Bean
    fun standardWebSocketClient(): StandardWebSocketClient {
        return StandardWebSocketClient()
    }
    @Bean
    fun clickRepositoryService() = ClickRepositoryServiceImpl(clickEntityRepository)

    @Bean
    fun myQueue1(): Queue? {
        return Queue("safe", false)
    }

    @Bean
    fun myQueue2(): Queue? {
        return Queue("csv", false)
    }

    @Bean
    fun exchange(): TopicExchange? {
        return TopicExchange("exchange")
    }

    @Bean
    fun bindingSafeBrowsing(@Qualifier("myQueue1") queue: Queue?, exchange: TopicExchange?): Binding? {
        return BindingBuilder.bind(queue).to(exchange).with("safe")
    }
    @Bean
    fun bindingCSV(@Qualifier("myQueue2") queue: Queue?, exchange: TopicExchange?): Binding? {
        return BindingBuilder.bind(queue).to(exchange).with("csv")
    }

    @Bean
    fun shortUrlRepositoryService() = ShortUrlRepositoryServiceImpl(shortUrlEntityRepository)

    @Bean
    fun validatorService() = ValidatorServiceImpl()

    @Bean
    fun rabbitMQService() = RabbitMQServiceImpl(rabbitTemplate,shortUrlRepositoryService())

    @Bean
    fun hashService() = HashServiceImpl()

    @Bean
    fun QRService() = QRServiceImpl()

    @Bean
    fun redirectUseCase() = RedirectUseCaseImpl(shortUrlRepositoryService())

    @Bean
    fun logClickUseCase() = LogClickUseCaseImpl(clickRepositoryService())


    @Bean
    fun webSocketService() = WebSocketServiceImpl(standardWebSocketClient())

    @Bean
    fun googleSafeBrowsing() = GoogleSafeBrowsingServiceImpl()


    @Bean
    fun createShortUrlUseCase() =
        CreateShortUrlUseCaseImpl(shortUrlRepositoryService(), validatorService(), hashService(), rabbitMQService())

    @Bean
    fun generateQRUseCase() = GenerateQRUseCaseImpl(shortUrlRepositoryService(), QRService())


    @Bean
    fun infoHTTPHeaderUserCase() = InfoHTTPHeaderCaseImpl(clickRepositoryService(),shortUrlRepositoryService())


}
@Configuration
@EnableWebSocket
class WebSocketConfiguration : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(MyWebSocketHandler(), "/ws").setAllowedOrigins("*")
    }
}

class MyWebSocketHandler : TextWebSocketHandler() {
    // Mapa que almacena el progreso y el identificador de cliente de cada sesión
    val progressMap = mutableMapOf<String, Pair<String, Int>>()
    val sessions = mutableListOf<WebSocketSession>()
    val clientSessions = mutableMapOf<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        // Genera un identificador único para la sesión
        val sessionId = session.id
        sessions.add(session)
        // Establece el identificador como un atributo de sesión
        // session.attributes["sessionId"] = sessionId

        clientSessions[sessionId] = session
        // Obtiene el identificador de cliente del atributo de sesión
        //val clientId = session.attributes["sessionId"] as? String ?: return

        // Agrega una nueva entrada al mapa para el identificador de sesión y el identificador de cliente
        //progressMap[sessionId] = Pair(clientId, 0)

    }
    private fun sendMessageToClient(clientId: String, message: TextMessage) {
        val session = clientSessions[clientId]
        if (session != null) {
            session.sendMessage(message)
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // Obtiene el identificador de la sesión del atributo de sesión
        //val sessionId = session.attributes["sessionId"] as? String ?: return
        //println(clientSessions[sessionId])
        //clientSessions[sessionId]?.sendMessage(message)

        val sessionId = session.id
        for(sess in sessions){
            println(sess.id)
            sess.sendMessage(message)
        }
        sendMessageToClient(sessionId,message)

        // Obtiene el identificador de cliente y el progreso actual del mapa
        //val entry = progressMap[sessionId]
        //if (entry == null) return
        // val (clientId, progress) = entry

        // Actualiza el progreso para el identificador de sesión en el mapa
        // if (message.payload.matches(Regex("^\\d+$"))) {
        //     progressMap[sessionId] = Pair(clientId, message.payload.toInt())
        // }


    }




}







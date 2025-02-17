package es.unizar.urlshortener

import GenerateQRUseCaseImpl
import es.unizar.urlshortener.core.usecases.*
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
    @Autowired val rabbitTemplate: RabbitTemplate
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
    fun exchange(): TopicExchange? {
        return TopicExchange("exchange")
    }

    @Bean
    fun bindingSafeBrowsing(@Qualifier("myQueue1") queue: Queue?, exchange: TopicExchange?): Binding? {
        return BindingBuilder.bind(queue).to(exchange).with("safe")
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
    fun retrieveQRUseCase() = RetrieveQRUseCaseImpl(shortUrlRepositoryService())

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

    private val clientSessions = mutableMapOf<String, WebSocketSession>()
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = message.payload
        if (payload.startsWith("clientId:")) {
            val clientIdJs = payload.split(":")[1]
            // Agrega el identificador de cliente al mapa de sesiones
            clientSessions[clientIdJs] = session
            return
        }
        val (body, clientId) = message.payload.split(":")
        clientSessions[clientId.trim()]?.sendMessage(TextMessage(body))

    }
}









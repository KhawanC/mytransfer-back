package br.com.khawantech.files.transferencia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    
    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ CORREÇÃO DE SEGURANÇA: CORS específico para o frontend autorizado
        registry.addEndpoint("/ws")
            .addInterceptors(webSocketAuthInterceptor)
            .setAllowedOrigins(frontendUrl)
            .withSockJS();
        
        registry.addEndpoint("/ws")
            .addInterceptors(webSocketAuthInterceptor)
            .setAllowedOrigins(frontendUrl);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // ✅ CORREÇÃO DE SEGURANÇA: Limites reduzidos de 50 MB para 8 MB
        // Chunk máximo: 5 MB binário = ~6.7 MB em Base64
        // Adicionar margem de segurança: 8 MB
        registration.setMessageSizeLimit(8 * 1024 * 1024); // 8 MB (anterior: 50 MB)
        registration.setSendBufferSizeLimit(8 * 1024 * 1024); // 8 MB (anterior: 50 MB)
        registration.setSendTimeLimit(60 * 1000); // 60 segundos
    }
}

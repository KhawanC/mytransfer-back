package br.com.khawantech.files.transferencia.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .addInterceptors(webSocketAuthInterceptor)
            .setAllowedOriginPatterns("*")
            .withSockJS();
        
        registry.addEndpoint("/ws")
            .addInterceptors(webSocketAuthInterceptor)
            .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Aumentar limites para suportar chunks grandes em Base64 (até ~7 MB em Base64 = ~5 MB binário)
        registration.setMessageSizeLimit(50 * 1024 * 1024); // 50 MB
        registration.setSendBufferSizeLimit(50 * 1024 * 1024); // 50 MB
        registration.setSendTimeLimit(60 * 1000); // 60 segundos
    }
}

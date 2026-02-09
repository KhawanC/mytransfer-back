package br.com.khawantech.files.transferencia.config;

import java.security.Principal;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            Object userIdObj = accessor.getSessionAttributes().get("userId");
            
            if (userIdObj != null) {
                String userId = userIdObj.toString();
                Principal principal = new UserIdPrincipal(userId);
                accessor.setUser(principal);
                
                log.debug("Principal configurado para userId: {}", userId);
            }
        }
        
        return message;
    }

    private static class UserIdPrincipal implements Principal {
        private final String userId;

        public UserIdPrincipal(String userId) {
            this.userId = userId;
        }

        @Override
        public String getName() {
            return userId;
        }
    }
}

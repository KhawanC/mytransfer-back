package br.com.khawantech.files.transferencia.config;

import java.security.Principal;
import java.util.Optional;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import br.com.khawantech.files.auth.service.JwtService;
import br.com.khawantech.files.user.entity.User;
import br.com.khawantech.files.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserService userService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            if (!ensureAuthenticated(accessor)) {
                return null;
            }

            var sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes == null) {
                return null;
            }

            Object userIdObj = sessionAttributes.get("userId");
            if (userIdObj != null) {
                String userId = userIdObj.toString();
                Principal principal = new UserIdPrincipal(userId);
                accessor.setUser(principal);
                log.debug("Principal configurado para userId: {}", userId);
            }
        }
        
        return message;
    }

    private boolean ensureAuthenticated(StompHeaderAccessor accessor) {
        var sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null && sessionAttributes.get("userId") != null) {
            return true;
        }

        if (sessionAttributes == null) {
            return false;
        }

        String authHeader = firstHeader(accessor, "Authorization");
        if (authHeader == null) {
            authHeader = firstHeader(accessor, "authorization");
        }

        String token;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = firstHeader(accessor, "token");
        }

        if (token == null || token.isBlank()) {
            log.warn("WebSocket CONNECT rejected: no token provided");
            return false;
        }

        try {
            String userEmail = jwtService.extractUsername(token);
            if (userEmail == null) {
                return false;
            }

            Optional<User> userOptional = userService.findByEmail(userEmail);
            if (userOptional.isEmpty()) {
                return false;
            }

            User user = userOptional.get();
            if (!jwtService.isTokenValid(token, user)) {
                return false;
            }

            sessionAttributes.put("user", user);
            sessionAttributes.put("userId", user.getId());

            return true;
        } catch (Exception e) {
            log.error("WebSocket CONNECT authentication failed: {}", e.getMessage());
            return false;
        }
    }

    private static String firstHeader(StompHeaderAccessor accessor, String name) {
        String value = accessor.getFirstNativeHeader(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return null;
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

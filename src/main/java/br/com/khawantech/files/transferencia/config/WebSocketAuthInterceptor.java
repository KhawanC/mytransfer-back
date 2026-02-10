package br.com.khawantech.files.transferencia.config;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.khawantech.files.auth.service.JwtService;
import br.com.khawantech.files.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final UserService userService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String token = extractToken(request);
        
        if (token == null) {
            return true;
        }

        try {
            String userEmail = jwtService.extractUsername(token);
            
            if (userEmail != null) {
                Optional<br.com.khawantech.files.user.entity.User> userOptional = userService.findByEmail(userEmail);
                
                if (userOptional.isPresent()) {
                    br.com.khawantech.files.user.entity.User user = userOptional.get();
                    
                    if (jwtService.isTokenValid(token, user)) {
                        attributes.put("user", user);
                        attributes.put("userId", user.getId());
                        
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, null, user.getAuthorities()
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        
                        log.debug("WebSocket handshake successful for user: {}", userEmail);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("WebSocket handshake failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query != null) {
            MultiValueMap<String, String> params = UriComponentsBuilder.newInstance().query(query).build().getQueryParams();
            String token = params.getFirst("token");
            if (token != null) {
                return token;
            }
        }

        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        return null;
    }
}

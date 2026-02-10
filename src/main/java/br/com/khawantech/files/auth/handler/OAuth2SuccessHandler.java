package br.com.khawantech.files.auth.handler;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.khawantech.files.auth.dto.AuthResponse;
import br.com.khawantech.files.auth.service.JwtService;
import br.com.khawantech.files.auth.service.OAuth2UserService;
import br.com.khawantech.files.user.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2UserService oAuth2UserService;
    private final JwtService jwtService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2User oAuth2User = oauthToken.getPrincipal();

            String googleId = oAuth2User.getAttribute("sub");
            String email = oAuth2User.getAttribute("email");
            String name = oAuth2User.getAttribute("name");

            log.info("OAuth2 authentication success for email: {}", email);

            User user = oAuth2UserService.processOAuth2User(googleId, email, name);

            AuthResponse authResponse = createAuthResponse(user);

            String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth/callback")
                .queryParam("access_token", authResponse.getAccessToken())
                .queryParam("refresh_token", authResponse.getRefreshToken())
                .queryParam("expires_in", authResponse.getExpiresIn())
                .build()
                .toUriString();

            log.info("Redirecting to: {}", frontendUrl + "/oauth/callback");
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        } else {
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }

    private AuthResponse createAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
            .id(user.getId())
            .email(user.getEmail())
            .name(user.getName())
            .authProvider(user.getAuthProvider().name())
            .userType(user.getUserType().name())
            .build();

        return AuthResponse.of(
            accessToken,
            refreshToken,
            jwtService.getAccessTokenExpiration(),
            userInfo
        );
    }
}

package br.com.khawantech.files.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.khawantech.files.auth.dto.AuthResponse;
import br.com.khawantech.files.auth.dto.LoginRequest;
import br.com.khawantech.files.auth.dto.RefreshTokenRequest;
import br.com.khawantech.files.auth.dto.RegisterRequest;
import br.com.khawantech.files.auth.exception.AuthenticationException;
import br.com.khawantech.files.user.entity.AuthProvider;
import br.com.khawantech.files.user.entity.User;
import br.com.khawantech.files.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        if (userService.existsByEmail(request.getEmail())) {
            throw new AuthenticationException("Email already registered");
        }

        User user = User.builder()
            .name(request.getName())
            .email(request.getEmail().toLowerCase())
            .password(passwordEncoder.encode(request.getPassword()))
            .authProvider(AuthProvider.LOCAL)
            .build();

        user = userService.save(user);
        log.info("User registered successfully: {}", user.getId());

        return createAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail().toLowerCase(),
                request.getPassword()
            )
        );

        User user = userService.findByEmail(request.getEmail().toLowerCase())
            .orElseThrow(() -> new AuthenticationException("User not found"));

        log.info("User logged in successfully: {}", user.getId());
        return createAuthResponse(user);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtService.validateToken(refreshToken)) {
            throw new AuthenticationException("Invalid refresh token");
        }

        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new AuthenticationException("Token is not a refresh token");
        }

        String email = jwtService.extractUsername(refreshToken);
        User user = userService.findByEmail(email)
            .orElseThrow(() -> new AuthenticationException("User not found"));

        log.info("Token refreshed for user: {}", user.getId());
        return createAuthResponse(user);
    }

    public AuthResponse createAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
            .id(user.getId())
            .email(user.getEmail())
            .name(user.getName())
            .authProvider(user.getAuthProvider().name())
            .build();

        return AuthResponse.of(
            accessToken,
            refreshToken,
            jwtService.getAccessTokenExpiration(),
            userInfo
        );
    }
}

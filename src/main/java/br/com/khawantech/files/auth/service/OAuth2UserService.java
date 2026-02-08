package br.com.khawantech.files.auth.service;

import org.springframework.stereotype.Service;

import br.com.khawantech.files.auth.exception.AuthenticationException;
import br.com.khawantech.files.user.entity.AuthProvider;
import br.com.khawantech.files.user.entity.User;
import br.com.khawantech.files.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService {

    private final UserService userService;

    public User processOAuth2User(String googleId, String email, String name) {
        log.info("Processing OAuth2 user with email: {}", email);

        return userService.findByGoogleId(googleId)
            .map(existingUser -> {
                existingUser.setName(name);
                existingUser.setEmail(email.toLowerCase());
                return userService.update(existingUser);
            })
            .orElseGet(() -> {
                if (userService.existsByEmail(email.toLowerCase())) {
                    User existingUser = userService.findByEmail(email.toLowerCase())
                        .orElseThrow(() -> new AuthenticationException("User not found"));
                    
                    existingUser.setGoogleId(googleId);
                    return userService.update(existingUser);
                }

                User newUser = User.builder()
                    .name(name)
                    .email(email.toLowerCase())
                    .googleId(googleId)
                    .authProvider(AuthProvider.GOOGLE)
                    .build();
                
                return userService.save(newUser);
            });
    }
}

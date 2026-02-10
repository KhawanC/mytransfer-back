package br.com.khawantech.files.user.service;

import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import br.com.khawantech.files.user.entity.AuthProvider;
import br.com.khawantech.files.user.entity.User;
import br.com.khawantech.files.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Cacheable(value = "user-by-id", key = "#id", unless = "#result == null || #result.isEmpty()")
    public Optional<User> findById(String id) {
        log.debug("Fetching user by id: {} from database", id);
        return userRepository.findById(id);
    }

    @Cacheable(value = "user-by-email", key = "#email", unless = "#result == null || #result.isEmpty()")
    public Optional<User> findByEmail(String email) {
        log.debug("Fetching user by email: {} from database", email);
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider) {
        return userRepository.findByEmailAndAuthProvider(email, authProvider);
    }

    public Optional<User> findByGoogleId(String googleId) {
        return userRepository.findByGoogleId(googleId);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Caching(evict = {
        @CacheEvict(value = "user-by-email", key = "#user.email"),
        @CacheEvict(value = "user-by-id", key = "#user.id", condition = "#user.id != null")
    })
    public User save(User user) {
        user.generateId();
        log.debug("Saving user: {}", user.getEmail());
        return userRepository.save(user);
    }

    @Caching(evict = {
        @CacheEvict(value = "user-by-email", key = "#user.email"),
        @CacheEvict(value = "user-by-id", key = "#user.id")
    })
    public User update(User user) {
        log.debug("Updating user: {}", user.getEmail());
        return userRepository.save(user);
    }

    @Caching(evict = {
        @CacheEvict(value = "user-by-email", key = "#user.email"),
        @CacheEvict(value = "user-by-id", key = "#user.id")
    })
    public void delete(User user) {
        log.debug("Deleting user: {}", user.getEmail());
        userRepository.delete(user);
    }
}

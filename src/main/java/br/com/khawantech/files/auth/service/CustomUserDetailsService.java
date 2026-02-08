package br.com.khawantech.files.auth.service;

import br.com.khawantech.files.user.entity.User;
import br.com.khawantech.files.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userService.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    public User loadUserById(String id) {
        return userService.findById(id)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
    }
}

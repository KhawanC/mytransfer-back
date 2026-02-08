package br.com.khawantech.files.user.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import br.com.khawantech.files.user.entity.AuthProvider;
import br.com.khawantech.files.user.entity.User;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);
}

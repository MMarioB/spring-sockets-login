package com.discobingohits.login_sockets_bingo.repository;

import com.discobingohits.login_sockets_bingo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByVerificationToken(String token);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}

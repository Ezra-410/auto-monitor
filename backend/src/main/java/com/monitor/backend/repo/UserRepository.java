package com.monitor.backend.repo;

import com.monitor.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    /** Look a user up by the Keycloak "sub" claim (D-24). */
    Optional<User> findByExternalSubject(String externalSubject);
}

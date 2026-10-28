package com.monitor.backend.service;

import com.monitor.backend.entity.Role;
import com.monitor.backend.entity.User;
import com.monitor.backend.repo.Repositories.RoleRepository;
import com.monitor.backend.repo.Repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminSeeder(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                       @Value("${app.admin-username:admin}") String adminUsername,
                       @Value("${app.admin-password:admin}") String adminPassword) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role admin = roles.findByName("ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ADMIN");
            return roles.save(r);
        });
        roles.findByName("USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("USER");
            return roles.save(r);
        });
        users.findByUsername(adminUsername).orElseGet(() -> {
            User u = new User();
            u.setUsername(adminUsername);
            u.setDisplayName("Administrateur");
            u.setPasswordHash(encoder.encode(adminPassword));
            u.setRoles(Set.of(admin));
            log.info("Compte admin '{}' créé", adminUsername);
            return users.save(u);
        });
    }
}

package com.monitor.backend.service;

import com.monitor.backend.entity.Role;
import com.monitor.backend.repo.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the application roles only. Milestone 3: no local admin account with a password any more;
 * administrators come from the LDAP "administrators" group through Keycloak (UserProvisioningService).
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private final RoleRepository roles;

    public AdminSeeder(RoleRepository roles) {
        this.roles = roles;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRole("ADMIN");
        ensureRole("USER");
    }

    private void ensureRole(String name) {
        roles.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            return roles.save(r);
        });
    }
}

package com.monitor.backend.security;

import java.util.List;

public record AuthUser(Long id, String username, List<String> roles) {

    public boolean isAdmin() {
        return roles != null && roles.contains("ADMIN");
    }
}

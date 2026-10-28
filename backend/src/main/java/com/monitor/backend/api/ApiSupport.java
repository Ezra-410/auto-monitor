package com.monitor.backend.api;

import com.monitor.backend.entity.Project;
import com.monitor.backend.repo.Repositories.ProjectRepository;
import com.monitor.backend.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ApiSupport {

    private final ProjectRepository projects;

    public ApiSupport(ProjectRepository projects) {
        this.projects = projects;
    }

    public static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    public Project requireProject(Long projectId) {
        return projects.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable"));
    }

    public Project requireAccess(Long projectId) {
        AuthUser user = currentUser();
        Project project = requireProject(projectId);
        if (!user.isAdmin() && !project.getOwner().getId().equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé à ce projet");
        }
        return project;
    }
}

package com.monitor.backend.api;

import com.monitor.backend.entity.Project;
import com.monitor.backend.entity.Repo;
import com.monitor.backend.entity.User;
import com.monitor.backend.repo.Repositories.ProjectRepository;
import com.monitor.backend.repo.Repositories.RepoRepository;
import com.monitor.backend.repo.Repositories.UserRepository;
import com.monitor.backend.security.AuthUser;
import com.monitor.backend.security.CryptoService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectRepository projects;
    private final RepoRepository repos;
    private final UserRepository users;
    private final CryptoService crypto;
    private final ApiSupport support;

    public ProjectController(ProjectRepository projects, RepoRepository repos, UserRepository users,
                             CryptoService crypto, ApiSupport support) {
        this.projects = projects;
        this.repos = repos;
        this.users = users;
        this.crypto = crypto;
        this.support = support;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        AuthUser user = ApiSupport.currentUser();
        List<Project> found = user.isAdmin() ? projects.findAll() : projects.findByOwnerId(user.id());
        return found.stream().map(this::toJson).toList();
    }

    public record CreateProject(@NotBlank String name, String description) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> create(@RequestBody CreateProject req) {
        AuthUser current = ApiSupport.currentUser();
        User owner = users.findById(current.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Project p = new Project();
        p.setName(req.name());
        p.setDescription(req.description());
        p.setOwner(owner);
        return toJson(projects.save(p));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> detail(@PathVariable Long id) {
        Project p = support.requireAccess(id);
        Map<String, Object> json = new java.util.HashMap<>(toJson(p));
        json.put("repositories", repos.findByProjectId(id).stream().map(r -> Map.of(
                "id", r.getId(),
                "url", r.getUrl(),
                "branch", r.getBranch())).toList());
        return json;
    }

    public record CreateRepo(@NotBlank String url, String branch, String token) {
    }

    @PostMapping("/{id}/repositories")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> addRepo(@PathVariable Long id, @RequestBody CreateRepo req) {
        Project p = support.requireAccess(id);
        Repo repo = new Repo();
        repo.setProject(p);
        repo.setUrl(req.url());
        repo.setBranch(req.branch() == null || req.branch().isBlank() ? "main" : req.branch());
        repo.setTokenEncrypted(crypto.encrypt(req.token()));
        Repo saved = repos.save(repo);
        return Map.of("id", saved.getId(), "url", saved.getUrl(), "branch", saved.getBranch());
    }

    private Map<String, Object> toJson(Project p) {
        return Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "description", p.getDescription() == null ? "" : p.getDescription(),
                "created_at", p.getCreatedAt().toString());
    }
}

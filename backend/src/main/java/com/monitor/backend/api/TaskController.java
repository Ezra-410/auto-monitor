package com.monitor.backend.api;

import com.monitor.backend.entity.Enums;
import com.monitor.backend.entity.Project;
import com.monitor.backend.entity.Task;
import com.monitor.backend.repo.Repositories.ObjectiveRepository;
import com.monitor.backend.repo.Repositories.TaskRepository;
import com.monitor.backend.repo.Repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskRepository tasks;
    private final ObjectiveRepository objectives;
    private final UserRepository users;
    private final ApiSupport support;

    public TaskController(TaskRepository tasks, ObjectiveRepository objectives,
                          UserRepository users, ApiSupport support) {
        this.tasks = tasks;
        this.objectives = objectives;
        this.users = users;
        this.support = support;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Task> list(@RequestParam("project_id") Long projectId) {
        support.requireAccess(projectId);
        return tasks.findByProjectId(projectId);
    }

    public record Upsert(Long project_id, Long objective_id, String title, String description,
                         String status, Long assignee_id, String due_date) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Task create(@RequestBody Upsert req) {
        Project p = support.requireAccess(req.project_id());
        Task t = new Task();
        t.setProject(p);
        apply(t, req);
        return tasks.save(t);
    }

    @PatchMapping("/{id}")
    @Transactional
    public Task update(@PathVariable Long id, @RequestBody Upsert req) {
        Task t = tasks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        support.requireAccess(t.getProject().getId());
        apply(t, req);
        return tasks.save(t);
    }

    private void apply(Task t, Upsert req) {
        if (req.title() != null) t.setTitle(req.title());
        if (req.description() != null) t.setDescription(req.description());
        if (req.due_date() != null) t.setDueDate(LocalDate.parse(req.due_date()));
        if (req.status() != null) t.setStatus(Enums.TaskStatus.valueOf(req.status()));
        if (req.objective_id() != null) {
            t.setObjective(objectives.findById(req.objective_id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Objectif inconnu")));
        }
        if (req.assignee_id() != null) {
            t.setAssignee(users.findById(req.assignee_id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Utilisateur inconnu")));
        }
    }
}

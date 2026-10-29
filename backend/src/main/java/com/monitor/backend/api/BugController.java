package com.monitor.backend.api;

import com.monitor.backend.entity.Bug;
import com.monitor.backend.entity.Enums;
import com.monitor.backend.entity.Project;
import com.monitor.backend.repo.BugRepository;
import com.monitor.backend.repo.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bugs")
public class BugController {

    private final BugRepository bugs;
    private final TaskRepository tasks;
    private final ApiSupport support;

    public BugController(BugRepository bugs, TaskRepository tasks, ApiSupport support) {
        this.bugs = bugs;
        this.tasks = tasks;
        this.support = support;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Bug> list(@RequestParam("project_id") Long projectId) {
        support.requireAccess(projectId);
        return bugs.findByProject_Id(projectId);
    }

    public record Upsert(Long project_id, String title, String description,
                         String severity, String status, Long task_id) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Bug create(@RequestBody Upsert req) {
        Project p = support.requireAccess(req.project_id());
        Bug b = new Bug();
        b.setProject(p);
        apply(b, req);
        return bugs.save(b);
    }

    @PatchMapping("/{id}")
    @Transactional
    public Bug update(@PathVariable Long id, @RequestBody Upsert req) {
        Bug b = bugs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        support.requireAccess(b.getProject().getId());
        apply(b, req);
        return bugs.save(b);
    }

    private void apply(Bug b, Upsert req) {
        if (req.title() != null) b.setTitle(req.title());
        if (req.description() != null) b.setDescription(req.description());
        if (req.severity() != null) b.setSeverity(Enums.BugSeverity.valueOf(req.severity()));
        if (req.status() != null) b.setStatus(Enums.BugStatus.valueOf(req.status()));
        if (req.task_id() != null) {
            b.setTask(tasks.findById(req.task_id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tâche inconnue")));
        }
    }
}

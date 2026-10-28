package com.monitor.backend.api;

import com.monitor.backend.entity.Enums;
import com.monitor.backend.entity.Objective;
import com.monitor.backend.entity.Project;
import com.monitor.backend.repo.Repositories.ObjectiveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/objectives")
public class ObjectiveController {

    private final ObjectiveRepository objectives;
    private final ApiSupport support;

    public ObjectiveController(ObjectiveRepository objectives, ApiSupport support) {
        this.objectives = objectives;
        this.support = support;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Objective> list(@RequestParam("project_id") Long projectId) {
        support.requireAccess(projectId);
        return objectives.findByProjectId(projectId);
    }

    public record Upsert(Long project_id, String title, String description,
                         String status, Integer priority, String due_date) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Objective create(@RequestBody Upsert req) {
        Project p = support.requireAccess(req.project_id());
        Objective o = new Objective();
        o.setProject(p);
        apply(o, req);
        return objectives.save(o);
    }

    @PatchMapping("/{id}")
    @Transactional
    public Objective update(@PathVariable Long id, @RequestBody Upsert req) {
        Objective o = objectives.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        support.requireAccess(o.getProject().getId());
        apply(o, req);
        return objectives.save(o);
    }

    private void apply(Objective o, Upsert req) {
        if (req.title() != null) o.setTitle(req.title());
        if (req.description() != null) o.setDescription(req.description());
        if (req.priority() != null) o.setPriority(req.priority());
        if (req.due_date() != null) o.setDueDate(LocalDate.parse(req.due_date()));
        if (req.status() != null) o.setStatus(Enums.ObjectiveStatus.valueOf(req.status()));
    }
}

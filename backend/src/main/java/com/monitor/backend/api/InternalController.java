package com.monitor.backend.api;

import com.monitor.backend.entity.*;
import com.monitor.backend.entity.Enums;
import com.monitor.backend.repo.*;
import com.monitor.backend.security.CryptoService;
import com.monitor.backend.service.WsEventPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API interne réservée à l'agent-worker (réseau Docker, X-Internal-Token).
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalController {

    private final ProjectRepository projects;
    private final RepoRepository repos;
    private final ObjectiveRepository objectives;
    private final TaskRepository tasks;
    private final BugRepository bugs;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserRepository users;
    private final CryptoService crypto;
    private final WsEventPublisher events;

    public InternalController(ProjectRepository projects, RepoRepository repos,
                              ObjectiveRepository objectives, TaskRepository tasks,
                              BugRepository bugs, ConversationRepository conversations,
                              MessageRepository messages, UserRepository users,
                              CryptoService crypto, WsEventPublisher events) {
        this.projects = projects;
        this.repos = repos;
        this.objectives = objectives;
        this.tasks = tasks;
        this.bugs = bugs;
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.crypto = crypto;
        this.events = events;
    }

    // ---------- Projets / contexte ----------

    @GetMapping("/projects")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listProjects() {
        return projects.findAll().stream()
                .map(p -> Map.<String, Object>of("id", p.getId(), "name", p.getName(),
                        "owner_id", p.getOwner().getId()))
                .toList();
    }

    @GetMapping("/projects/{id}/context")
    @Transactional(readOnly = true)
    public Map<String, Object> context(@PathVariable Long id) {
        Project p = requireProject(id);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("project", Map.of("id", p.getId(), "name", p.getName(),
                "owner_id", p.getOwner().getId(),
                "description", p.getDescription() == null ? "" : p.getDescription()));
        ctx.put("objectives", objectives.findByProject_Id(id));
        ctx.put("tasks", tasks.findByProject_Id(id));
        ctx.put("bugs", bugs.findByProject_Id(id));
        ctx.put("repositories", repos.findByProject_Id(id).stream()
                .map(r -> Map.of("id", r.getId(), "url", r.getUrl(), "branch", r.getBranch()))
                .toList());
        return ctx;
    }

    @GetMapping("/repositories/{id}/credential")
    @Transactional(readOnly = true)
    public Map<String, Object> repoCredential(@PathVariable Long id) {
        Repo r = repos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of(
                "id", r.getId(),
                "url", r.getUrl(),
                "branch", r.getBranch(),
                "token", crypto.decrypt(r.getTokenEncrypted()) == null ? "" : crypto.decrypt(r.getTokenEncrypted()));
    }

    // ---------- Objectifs ----------

    public record ObjectiveUpsert(Long project_id, String title, String description,
                                  String status, Integer priority, String due_date) {
    }

    @GetMapping("/objectives")
    @Transactional(readOnly = true)
    public List<Objective> listObjectives(@RequestParam("project_id") Long projectId) {
        return objectives.findByProject_Id(projectId);
    }

    @PostMapping("/objectives")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Objective createObjective(@RequestBody ObjectiveUpsert req) {
        Objective o = new Objective();
        o.setProject(requireProject(req.project_id()));
        applyObjective(o, req);
        Objective saved = objectives.save(o);
        publish("objective.created", saved.getProject().getId(), null, objectiveMap(saved));
        return saved;
    }

    @PatchMapping("/objectives/{id}")
    @Transactional
    public Objective updateObjective(@PathVariable Long id, @RequestBody ObjectiveUpsert req) {
        Objective o = objectives.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        applyObjective(o, req);
        Objective saved = objectives.save(o);
        publish("objective.updated", saved.getProject().getId(), null, objectiveMap(saved));
        return saved;
    }

    private void applyObjective(Objective o, ObjectiveUpsert req) {
        if (req.title() != null) o.setTitle(req.title());
        if (req.description() != null) o.setDescription(req.description());
        if (req.priority() != null) o.setPriority(req.priority());
        if (req.due_date() != null) o.setDueDate(LocalDate.parse(req.due_date()));
        if (req.status() != null) o.setStatus(Enums.ObjectiveStatus.valueOf(req.status()));
    }

    // ---------- Tâches ----------

    public record TaskUpsert(Long project_id, Long objective_id, String title, String description,
                             String status, Long assignee_id, String due_date, String created_by) {
    }

    @GetMapping("/tasks")
    @Transactional(readOnly = true)
    public List<Task> listTasks(@RequestParam("project_id") Long projectId) {
        return tasks.findByProject_Id(projectId);
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Task createTask(@RequestBody TaskUpsert req) {
        Task t = new Task();
        t.setProject(requireProject(req.project_id()));
        applyTask(t, req);
        if (req.created_by() != null) {
            t.setCreatedBy(Enums.TaskCreator.valueOf(req.created_by()));
        } else {
            t.setCreatedBy(Enums.TaskCreator.AGENT);
        }
        Task saved = tasks.save(t);
        publish("task.created", saved.getProjectId(), null, taskMap(saved));
        return saved;
    }

    @PatchMapping("/tasks/{id}")
    @Transactional
    public Task updateTask(@PathVariable Long id, @RequestBody TaskUpsert req) {
        Task t = tasks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        applyTask(t, req);
        Task saved = tasks.save(t);
        publish("task.updated", saved.getProjectId(), null, taskMap(saved));
        return saved;
    }

    private void applyTask(Task t, TaskUpsert req) {
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

    // ---------- Bugs ----------

    public record BugUpsert(Long project_id, String title, String description,
                            String severity, String status, Long task_id) {
    }

    @GetMapping("/bugs")
    @Transactional(readOnly = true)
    public List<Bug> listBugs(@RequestParam("project_id") Long projectId) {
        return bugs.findByProject_Id(projectId);
    }

    @PostMapping("/bugs")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Bug createBug(@RequestBody BugUpsert req) {
        Bug b = new Bug();
        b.setProject(requireProject(req.project_id()));
        applyBug(b, req);
        Bug saved = bugs.save(b);
        publish("bug.created", saved.getProjectId(), null, bugMap(saved));
        return saved;
    }

    @PatchMapping("/bugs/{id}")
    @Transactional
    public Bug updateBug(@PathVariable Long id, @RequestBody BugUpsert req) {
        Bug b = bugs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        applyBug(b, req);
        Bug saved = bugs.save(b);
        publish("bug.updated", saved.getProjectId(), null, bugMap(saved));
        return saved;
    }

    private void applyBug(Bug b, BugUpsert req) {
        if (req.title() != null) b.setTitle(req.title());
        if (req.description() != null) b.setDescription(req.description());
        if (req.severity() != null) b.setSeverity(Enums.BugSeverity.valueOf(req.severity()));
        if (req.status() != null) b.setStatus(Enums.BugStatus.valueOf(req.status()));
        if (req.task_id() != null) {
            b.setTask(tasks.findById(req.task_id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tâche inconnue")));
        }
    }

    // ---------- Messages agent ----------

    public record AgentMessage(@NotNull Long project_id, @NotNull Long user_id,
                               Long conversation_id, @NotBlank String content) {
    }

    @PostMapping("/chat/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Message postAgentMessage(@Valid @RequestBody AgentMessage req) {
        Project p = requireProject(req.project_id());
        User user = users.findById(req.user_id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur inconnu"));
        Conversation conv = (req.conversation_id() != null
                ? conversations.findById(req.conversation_id())
                : conversations.findByProject_IdAndUser_Id(p.getId(), user.getId()))
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setProject(p);
                    c.setUser(user);
                    return conversations.save(c);
                });
        Message m = new Message();
        m.setConversation(conv);
        m.setAuthor(Enums.MessageAuthor.AGENT);
        m.setContent(req.content());
        Message saved = messages.save(m);
        publish("message.created", p.getId(), user.getId(), messageMap(saved));
        return saved;
    }

    @GetMapping("/conversations/{id}/messages")
    @Transactional(readOnly = true)
    public List<Message> recentMessages(@PathVariable Long id) {
        return messages.findTop50ByConversation_IdOrderByIdDesc(id);
    }

    // ---------- Helpers ----------

    private Project requireProject(Long id) {
        return projects.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable"));
    }

    private void publish(String type, Long projectId, Long userId, Object payload) {
        events.publish(type, projectId, userId, payload);
    }

    private Map<String, Object> objectiveMap(Objective o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId());
        m.put("title", o.getTitle());
        m.put("status", o.getStatus().name());
        m.put("priority", o.getPriority());
        m.put("due_date", o.getDueDate() == null ? null : o.getDueDate().toString());
        return m;
    }

    private Map<String, Object> taskMap(Task t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("title", t.getTitle());
        m.put("status", t.getStatus().name());
        m.put("objective_id", t.getObjectiveId());
        m.put("assignee", t.getAssigneeName());
        m.put("due_date", t.getDueDate() == null ? null : t.getDueDate().toString());
        m.put("created_by", t.getCreatedBy().name());
        return m;
    }

    private Map<String, Object> bugMap(Bug b) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", b.getId());
        m.put("title", b.getTitle());
        m.put("severity", b.getSeverity().name());
        m.put("status", b.getStatus().name());
        m.put("task_id", b.getTaskId());
        return m;
    }

    private Map<String, Object> messageMap(Message m) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", m.getId());
        out.put("conversation_id", m.getConversationId());
        out.put("author", m.getAuthor().name());
        out.put("content", m.getContent());
        out.put("created_at", m.getCreatedAt().toString());
        return out;
    }
}

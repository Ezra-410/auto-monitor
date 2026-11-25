package com.monitor.backend.api;

import com.monitor.backend.repo.NoteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Milestone 4: the machine-to-machine endpoint (Client Credentials, client conote-sync).
 * Only service accounts with the scope notes:read may call it (SecurityConfig, D-31).
 * It returns change METADATA only: note ids and timestamps. No titles, no bodies, no user
 * identities, so a machine never sees a student's or teacher's content.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final NoteRepository notes;

    public SyncController(NoteRepository notes) {
        this.notes = notes;
    }

    @GetMapping("/notes")
    @Transactional(readOnly = true)
    public Map<String, Object> changedNotes(
            @RequestParam(name = "since", required = false) String since,
            Authentication caller) {
        Instant from;
        try {
            from = since == null || since.isBlank() ? Instant.EPOCH : Instant.parse(since);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "since must be an ISO-8601 instant, e.g. 2026-11-25T00:00:00Z");
        }
        List<Map<String, Object>> changes = notes.findTop500ByUpdatedAtAfterOrderByUpdatedAtAsc(from).stream()
                .map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", n.getId());
                    m.put("updated_at", n.getUpdatedAt());
                    return m;
                })
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client", caller.getName());
        body.put("since", from);
        body.put("count", changes.size());
        body.put("changes", changes);
        return body;
    }
}

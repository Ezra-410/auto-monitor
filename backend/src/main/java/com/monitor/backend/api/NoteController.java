package com.monitor.backend.api;

import com.monitor.backend.entity.Note;
import com.monitor.backend.repo.NoteRepository;
import com.monitor.backend.repo.UserRepository;
import com.monitor.backend.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Milestone 4: the protected notes API.
 * WHO may call it is decided in SecurityConfig before this code runs (D-28):
 *   GET            -> a human user whose token has the scope notes:read
 *   POST/PUT/DELETE -> a human user whose token has the scope notes:write
 * WHICH notes they see is decided here: only their own (ownership check in every query).
 */
@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteRepository notes;
    private final UserRepository users;

    public NoteController(NoteRepository notes, UserRepository users) {
        this.notes = notes;
        this.users = users;
    }

    public record NoteRequest(String title, String body) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Note> list() {
        return notes.findByOwner_IdOrderByUpdatedAtDesc(ApiSupport.currentUser().id());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Note get(@PathVariable Long id) {
        return own(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Note create(@RequestBody NoteRequest req) {
        AuthUser me = ApiSupport.currentUser();
        Note n = new Note();
        n.setOwner(users.getReferenceById(me.id()));
        apply(n, req, true);
        return notes.save(n);
    }

    @PutMapping("/{id}")
    @Transactional
    public Note update(@PathVariable Long id, @RequestBody NoteRequest req) {
        Note n = own(id);
        apply(n, req, false);
        n.setUpdatedAt(Instant.now());
        return notes.save(n);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        notes.delete(own(id));
    }

    /** SECURITY CHECK: a note is only found if it belongs to the caller; otherwise 404. */
    private Note own(Long id) {
        return notes.findByIdAndOwner_Id(id, ApiSupport.currentUser().id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "note not found"));
    }

    private static void apply(Note n, NoteRequest req, boolean creating) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        if (req.title() != null || creating) {
            String title = req.title() == null ? "" : req.title().trim();
            if (title.isEmpty() || title.length() > 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must be 1-200 characters");
            }
            n.setTitle(title);
        }
        if (req.body() != null) {
            if (req.body().length() > 20_000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body too long");
            }
            n.setBody(req.body());
        }
    }
}

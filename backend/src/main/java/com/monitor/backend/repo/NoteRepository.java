package com.monitor.backend.repo;

import com.monitor.backend.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByOwner_IdOrderByUpdatedAtDesc(Long ownerId);

    /** Ownership is part of the query: another user's note simply isn't found (404, not 403). */
    Optional<Note> findByIdAndOwner_Id(Long id, Long ownerId);

    /** For the service account (D-31): what changed since a given time, across all users. */
    List<Note> findTop500ByUpdatedAtAfterOrderByUpdatedAtAsc(Instant since);
}

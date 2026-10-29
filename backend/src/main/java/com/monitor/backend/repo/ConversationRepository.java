package com.monitor.backend.repo;

import com.monitor.backend.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByProject_IdAndUser_Id(Long projectId, Long userId);

    List<Conversation> findByProject_Id(Long projectId);
}

package com.monitor.backend.repo;

import com.monitor.backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversation_IdOrderByIdAsc(Long conversationId);

    List<Message> findTop50ByConversation_IdOrderByIdDesc(Long conversationId);
}

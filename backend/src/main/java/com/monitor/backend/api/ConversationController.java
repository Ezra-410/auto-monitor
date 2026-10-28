package com.monitor.backend.api;

import com.monitor.backend.entity.Conversation;
import com.monitor.backend.entity.Message;
import com.monitor.backend.repo.Repositories.ConversationRepository;
import com.monitor.backend.repo.Repositories.MessageRepository;
import com.monitor.backend.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ApiSupport support;

    public ConversationController(ConversationRepository conversations, MessageRepository messages,
                                  ApiSupport support) {
        this.conversations = conversations;
        this.messages = messages;
        this.support = support;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Conversation> list(@RequestParam("project_id") Long projectId) {
        AuthUser user = ApiSupport.currentUser();
        support.requireAccess(projectId);
        return conversations.findByProjectId(projectId).stream()
                .filter(c -> user.isAdmin() || c.getUserId().equals(user.id()))
                .toList();
    }

    @GetMapping("/{id}/messages")
    @Transactional(readOnly = true)
    public List<Message> messages(@PathVariable Long id) {
        AuthUser user = ApiSupport.currentUser();
        Conversation c = conversations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!user.isAdmin() && !c.getUserId().equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return messages.findByConversationIdOrderByIdAsc(id);
    }
}

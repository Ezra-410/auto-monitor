package com.monitor.backend.api;

import com.monitor.backend.entity.Conversation;
import com.monitor.backend.entity.Enums;
import com.monitor.backend.entity.Message;
import com.monitor.backend.entity.Project;
import com.monitor.backend.entity.User;
import com.monitor.backend.repo.Repositories.ConversationRepository;
import com.monitor.backend.repo.Repositories.MessageRepository;
import com.monitor.backend.repo.Repositories.UserRepository;
import com.monitor.backend.security.AuthUser;
import com.monitor.backend.service.AgentClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserRepository users;
    private final ApiSupport support;
    private final AgentClient agentClient;

    public ChatController(ConversationRepository conversations, MessageRepository messages,
                          UserRepository users, ApiSupport support, AgentClient agentClient) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.support = support;
        this.agentClient = agentClient;
    }

    public record ChatMessage(@NotNull Long project_id, Long conversation_id, @NotBlank String content) {
    }

    @PostMapping("/messages")
    @Transactional
    public Message post(@Valid @RequestBody ChatMessage req) {
        AuthUser current = ApiSupport.currentUser();
        Project project = support.requireAccess(req.project_id());
        User user = users.findById(current.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        Conversation conv;
        if (req.conversation_id() != null) {
            conv = conversations.findById(req.conversation_id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!conv.getUserId().equals(current.id()) && !current.isAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } else {
            conv = conversations.findByProjectIdAndUserId(project.getId(), current.id())
                    .orElseGet(() -> {
                        Conversation c = new Conversation();
                        c.setProject(project);
                        c.setUser(user);
                        return conversations.save(c);
                    });
        }

        Message m = new Message();
        m.setConversation(conv);
        m.setAuthor(Enums.MessageAuthor.USER);
        m.setContent(req.content());
        Message saved = messages.save(m);

        agentClient.forwardUserMessage(project.getId(), conv.getId(), current.id(), req.content());
        return saved;
    }
}

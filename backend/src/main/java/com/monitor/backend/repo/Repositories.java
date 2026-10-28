package com.monitor.backend.repo;

import com.monitor.backend.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public final class Repositories {

    private Repositories() {
    }

    public interface UserRepository extends JpaRepository<User, Long> {
        Optional<User> findByUsername(String username);
    }

    public interface RoleRepository extends JpaRepository<Role, Long> {
        Optional<Role> findByName(String name);
    }

    public interface ProjectRepository extends JpaRepository<Project, Long> {
        List<Project> findByOwnerId(Long ownerId);
    }

    public interface RepoRepository extends JpaRepository<Repo, Long> {
        List<Repo> findByProjectId(Long projectId);
    }

    public interface ObjectiveRepository extends JpaRepository<Objective, Long> {
        List<Objective> findByProjectId(Long projectId);
    }

    public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
        List<Milestone> findByObjectiveId(Long objectiveId);
    }

    public interface TaskRepository extends JpaRepository<Task, Long> {
        List<Task> findByProjectId(Long projectId);
    }

    public interface BugRepository extends JpaRepository<Bug, Long> {
        List<Bug> findByProjectId(Long projectId);
    }

    public interface ConversationRepository extends JpaRepository<Conversation, Long> {
        Optional<Conversation> findByProjectIdAndUserId(Long projectId, Long userId);

        List<Conversation> findByProjectId(Long projectId);
    }

    public interface MessageRepository extends JpaRepository<Message, Long> {
        List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

        List<Message> findTop50ByConversationIdOrderByIdDesc(Long conversationId);
    }
}

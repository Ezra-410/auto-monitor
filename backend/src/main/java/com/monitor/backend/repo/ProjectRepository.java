package com.monitor.backend.repo;

import com.monitor.backend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOwner_Id(Long ownerId);
}

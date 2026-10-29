package com.monitor.backend.repo;

import com.monitor.backend.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByObjective_Id(Long objectiveId);
}

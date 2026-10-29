package com.monitor.backend.repo;

import com.monitor.backend.entity.Bug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BugRepository extends JpaRepository<Bug, Long> {
    List<Bug> findByProject_Id(Long projectId);
}

package com.monitor.backend.repo;

import com.monitor.backend.entity.Objective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObjectiveRepository extends JpaRepository<Objective, Long> {
    List<Objective> findByProject_Id(Long projectId);
}

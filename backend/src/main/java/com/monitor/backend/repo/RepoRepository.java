package com.monitor.backend.repo;

import com.monitor.backend.entity.Repo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepoRepository extends JpaRepository<Repo, Long> {
    List<Repo> findByProject_Id(Long projectId);
}

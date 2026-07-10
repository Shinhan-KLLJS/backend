package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
}

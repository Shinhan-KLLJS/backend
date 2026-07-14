package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.TeamBusinessRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 팀마다 사업자등록 정보 한 건만 존재한다 (team_id가 UNIQUE).
 */
public interface TeamBusinessRegistrationRepository extends JpaRepository<TeamBusinessRegistration, Long> {

    Optional<TeamBusinessRegistration> findByTeamId(Long teamId);
}

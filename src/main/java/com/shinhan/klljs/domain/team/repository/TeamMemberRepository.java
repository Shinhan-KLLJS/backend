package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @Query("select tm.team.id from TeamMember tm where tm.user.id = :userId and tm.status = :status")
    List<Long> findTeamIdsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") TeamMemberStatus status);

    boolean existsByUserIdAndTeamIdAndStatus(Long userId, Long teamId, TeamMemberStatus status);
}

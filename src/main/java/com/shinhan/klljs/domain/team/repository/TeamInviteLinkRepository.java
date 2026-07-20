package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.TeamInviteLink;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeamInviteLinkRepository extends JpaRepository<TeamInviteLink, Long> {

    Optional<TeamInviteLink> findByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from TeamInviteLink invite where invite.id = :inviteId")
    Optional<TeamInviteLink> findByIdForUpdate(@Param("inviteId") Long inviteId);

    Optional<TeamInviteLink> findByTeamIdAndRevokedAtIsNull(Long teamId);
}

package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.TeamInviteLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamInviteLinkRepository extends JpaRepository<TeamInviteLink, Long> {

    Optional<TeamInviteLink> findByTokenHash(byte[] tokenHash);
}

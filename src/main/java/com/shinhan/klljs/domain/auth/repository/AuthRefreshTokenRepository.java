package com.shinhan.klljs.domain.auth.repository;

import com.shinhan.klljs.domain.auth.entity.AuthRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AuthRefreshToken t where t.tokenHash = :tokenHash")
    Optional<AuthRefreshToken> findByTokenHashForUpdate(@Param("tokenHash") byte[] tokenHash);

    /** 잠금 없는 단순 조회 — 락이 필요 없는 조회/진단용. */
    Optional<AuthRefreshToken> findByTokenHash(byte[] tokenHash);

    List<AuthRefreshToken> findByTokenFamilyId(byte[] tokenFamilyId);

    List<AuthRefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
}

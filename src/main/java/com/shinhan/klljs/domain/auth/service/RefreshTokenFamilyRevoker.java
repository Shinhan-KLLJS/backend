package com.shinhan.klljs.domain.auth.service;

import com.shinhan.klljs.domain.auth.repository.AuthRefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 재사용 탐지 시 family 전체 폐기를 별도의 독립 트랜잭션(REQUIRES_NEW)으로 즉시 커밋한다.
 * RefreshTokenService.rotate()는 이 폐기를 호출한 뒤 예외를 던져 자신의 트랜잭션을 롤백하는데,
 * 같은 트랜잭션 안에서 처리하면 그 롤백에 이 폐기까지 함께 사라져버린다 —
 * 그래서 반드시 별개의 빈으로 분리해 REQUIRES_NEW가 실제로 새 트랜잭션을 열도록 한다
 * (같은 클래스 안에서 this.메서드() 호출로는 프록시를 안 거쳐 REQUIRES_NEW가 무시된다).
 */
@Service
@RequiredArgsConstructor
class RefreshTokenFamilyRevoker {

    private final AuthRefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(byte[] familyId, LocalDateTime now) {
        refreshTokenRepository.findByTokenFamilyId(familyId).stream()
                .filter(token -> token.getRevokedAt() == null)
                .forEach(token -> token.revoke(now));
    }
}

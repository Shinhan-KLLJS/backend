package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.TeamBusinessRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * 팀마다 사업자등록 정보 한 건만 존재한다 (team_id가 UNIQUE).
 * 그래서 재제출은 INSERT가 아니라 기존 행 UPDATE로 동작하고, findByTeamId가 그 진입점이 된다.
 */
public interface TeamBusinessRegistrationRepository extends JpaRepository<TeamBusinessRegistration, Long> {

    Optional<TeamBusinessRegistration> findByTeamId(Long teamId);

    /**
     * 주어진 S3 키들 중 <b>실제로 참조되고 있는</b> 것만 골라 돌려준다 (orphan 정리 배치용).
     *
     * 배치가 S3 목록을 한 페이지 읽을 때마다 이 메서드로 한 번만 물어본다 - 키 하나씩 조회하면
     * 페이지당 1000번의 쿼리가 나간다 (team-creation-api-spec.md 9절: "DB 참조 키를 batch 조회").
     * 여기 없는 키가 곧 orphan이다.
     */
    @Query("select r.documentStorageKey from TeamBusinessRegistration r where r.documentStorageKey in :keys")
    Set<String> findReferencedKeys(@Param("keys") Collection<String> keys);
}

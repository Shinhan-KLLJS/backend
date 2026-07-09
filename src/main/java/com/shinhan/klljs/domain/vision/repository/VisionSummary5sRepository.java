package com.shinhan.klljs.domain.vision.repository;

import com.shinhan.klljs.domain.vision.entity.VisionSummary5s;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VisionSummary5sRepository extends JpaRepository<VisionSummary5s, Long> {

    /**
     * 실시간 그래프(5-1)용. 특정 캠페인의 [fromUtc, toUtc) 구간(=오늘 KST를 UTC로 변환한 범위)
     * 안에서, cursorUtc 이후(첫 조회면 cursorUtc=null)의 포인트를 최신순으로 최대
     * pageable.getPageSize()개 가져온다.
     *
     * 오름차순(ORDER BY ASC)이 아니라 내림차순으로 가져오는 이유: "최근 N개"를 뽑으려면
     * 최신 것부터 담아야 LIMIT이 정확히 최근 N개를 의미하게 된다. 오름차순으로 LIMIT을 걸면
     * "가장 오래된 N개"가 나와버린다. 서비스 계층에서 이 결과를 다시 뒤집어(reverse)
     * eventTime 오름차순으로 응답한다.
     *
     * cursorUtc는 서비스 계층에서 이미 overlapSec만큼 앞당긴 값을 넘겨받는다 (SQS 메시지가
     * 늦게 도착하거나 순서가 바뀌는 걸 감안해 약간 겹쳐서 조회하기 위함 - 스펙 5-1절 참고).
     */
    @Query("""
            select v from VisionSummary5s v
            where v.campaign.id = :campaignId
              and v.eventTime >= :fromUtc and v.eventTime < :toUtc
              and (:cursorUtc is null or v.eventTime > :cursorUtc)
            order by v.eventTime desc
            """)
    List<VisionSummary5s> findRecentPoints(
            @Param("campaignId") Long campaignId,
            @Param("fromUtc") LocalDateTime fromUtc,
            @Param("toUtc") LocalDateTime toUtc,
            @Param("cursorUtc") LocalDateTime cursorUtc,
            Pageable pageable
    );
}

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

    /**
     * 시간별 누적 그래프(5-2)용. 특정 캠페인의 [fromUtc, toUtc) 구간에 속한 모든 5초 row를
     * 이벤트 시각 오름차순으로 전부 가져온다. 1시간 단위 합산은 서비스 계층(HourlyGraphService)의
     * 자바 코드에서 처리한다 - H2(로컬)와 MySQL(운영)마다 날짜 절삭 함수 문법이 달라서,
     * DB 방언에 의존하는 GROUP BY 대신 원본 row를 그대로 가져와 집계하는 쪽을 택했다
     * (스펙 9절 "시간대별 집계 테이블" 항목 참고 - 지금은 API 호출 시 5초 원천 데이터를
     * 그대로 집계하는 MVP 단계라, 트래픽이 커지면 이 메서드를 별도 집계 테이블/캐시로 교체한다).
     */
    @Query("""
            select v from VisionSummary5s v
            where v.campaign.id = :campaignId
              and v.eventTime >= :fromUtc and v.eventTime < :toUtc
            order by v.eventTime asc
            """)
    List<VisionSummary5s> findAllInRange(
            @Param("campaignId") Long campaignId,
            @Param("fromUtc") LocalDateTime fromUtc,
            @Param("toUtc") LocalDateTime toUtc
    );

    /**
     * 깔대기 그래프(6절)용. 특정 캠페인의 [fromUtc, toUtc) 구간 전체에 대한 ots_count/lts_count
     * 합계를 DB에서 바로 계산해서 가져온다. 5-2와 달리 시간대별로 쪼갤 필요 없이 구간 전체의
     * 합계 하나만 있으면 되므로, row를 통째로 끌고 오는 대신 SUM을 DB에 위임한다
     * (coalesce로 감싸 대상 row가 0건이어도 null이 아니라 0을 반환).
     */
    @Query("""
            select new com.shinhan.klljs.domain.vision.repository.VisionSummary5sRepository$PopulationSums(
                coalesce(sum(v.otsCount), 0), coalesce(sum(v.ltsCount), 0))
            from VisionSummary5s v
            where v.campaign.id = :campaignId
              and v.eventTime >= :fromUtc and v.eventTime < :toUtc
            """)
    PopulationSums sumPopulationInRange(
            @Param("campaignId") Long campaignId,
            @Param("fromUtc") LocalDateTime fromUtc,
            @Param("toUtc") LocalDateTime toUtc
    );

    /** sumPopulationInRange()의 결과. exposedPopulationCount=ots_count 합, attentionPopulationCount=lts_count 합. */
    record PopulationSums(long exposedPopulationCount, long attentionPopulationCount) {
    }
}

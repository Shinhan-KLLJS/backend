package com.shinhan.klljs.domain.campaign.repository;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /**
     * 캠페인 목록 조회용. 팀 ID 여러 개를 한 번에 받아서(사용자가 여러 팀에 속할 수 있으므로)
     * 그 팀들 소유의 캠페인을 전부 가져온다. Spring Data JPA가 메서드 이름만 보고
     * "Campaign.team.id in (:teamIds)" 쿼리를 자동으로 만들어준다 (Campaign 엔티티의
     * team 연관관계를 타고 들어가는 파생 쿼리라 별도 @Query 없이도 동작한다).
     *
     * keyword/status 필터링과 정렬은 여기서 하지 않고 서비스 계층에서 처리한다 -
     * 팀당 캠페인 개수가 많지 않을 걸로 예상되어(수십 건 단위), DB 쿼리를 필터 조합별로
     * 여러 개 만드는 것보다 일단 다 가져와서 메모리에서 거르는 편이 코드가 단순하다.
     */
    List<Campaign> findByTeamIdIn(List<Long> teamIds);

    /**
     * 팀 캠페인 페이지(캠페인 목록) 조회용. mediaLocationAddress를 응답에 내려줘야 해서
     * mediaUnit을 함께 fetch해 N+1을 피한다 - findByTeamIdIn과 달리 매체 조인이 필요하다.
     */
    @Query("select c from Campaign c join fetch c.mediaUnit where c.team.id = :teamId")
    List<Campaign> findByTeamIdWithMediaUnit(@Param("teamId") Long teamId);

    /**
     * SQS consumer가 Vision 메시지 하나를 어느 캠페인에 귀속시킬지 찾을 때 쓴다
     * (스펙 5-1절 "매체와 event_time 기준으로 현재 송출 중인 캠페인을 연결한다").
     * event_time의 KST 날짜가 캠페인 집행 기간(execution_start_date ~ execution_end_date)
     * 안에 들어오는 캠페인을 찾는다. 등록 시 매체 잠금과 기간 충돌 검증으로 한 건만 존재해야 하지만,
     * 과거 데이터나 수동 DB 변경의 불일치를 탐지하기 위해 List로 받아 fan-out 계층에서 모호성을 처리한다.
     */
    @Query("""
            select c from Campaign c
            where c.mediaUnit.id = :mediaUnitId
              and c.status <> :excludedStatus
              and c.executionStartDate <= :eventDateKst
              and c.executionEndDate >= :eventDateKst
            """)
    List<Campaign> findActiveCampaignsForMediaUnit(
            @Param("mediaUnitId") Long mediaUnitId,
            @Param("eventDateKst") LocalDate eventDateKst,
            @Param("excludedStatus") CampaignStatus excludedStatus
    );

    /** 선택 기간과 겹치는 캠페인이 있는 매체 ID만 한 번에 조회해 목록 N+1을 방지한다. */
    @Query("""
            select distinct c.mediaUnit.id from Campaign c
            where c.mediaUnit.id in :mediaUnitIds
              and c.status <> :excludedStatus
              and c.executionStartDate <= :requestedEndDate
              and c.executionEndDate >= :requestedStartDate
            """)
    List<Long> findConflictingMediaUnitIds(
            @Param("mediaUnitIds") List<Long> mediaUnitIds,
            @Param("excludedStatus") CampaignStatus excludedStatus,
            @Param("requestedStartDate") LocalDate requestedStartDate,
            @Param("requestedEndDate") LocalDate requestedEndDate
    );

    /** 매체 잠금 안에서 최종 기간 충돌을 다시 확인한다. 경계 날짜가 닿는 경우도 충돌이다. */
    @Query("""
            select count(c) from Campaign c
            where c.mediaUnit.id = :mediaUnitId
              and c.status <> :excludedStatus
              and c.executionStartDate <= :requestedEndDate
              and c.executionEndDate >= :requestedStartDate
            """)
    long countPeriodConflicts(
            @Param("mediaUnitId") Long mediaUnitId,
            @Param("excludedStatus") CampaignStatus excludedStatus,
            @Param("requestedStartDate") LocalDate requestedStartDate,
            @Param("requestedEndDate") LocalDate requestedEndDate
    );

    /** 등록 실패는 수동 확인 대상이므로 날짜 기반 자동 상태 보정에서 제외한다. */
    List<Campaign> findAllByStatusNot(CampaignStatus excludedStatus);

    /**
     * 삭제 API 전용. 같은 캠페인에 대한 동시 삭제 요청을 직렬화한다 - 잠금 없이 findById로
     * 읽으면 두 요청이 같은 행을 동시에 읽어 둘 다 삭제를 시도할 수 있고, 그러면 나중에
     * 커밋하는 쪽은 이미 지워진 행에 DELETE를 실행해 Hibernate가 stale-state 예외를 던진다
     * (0 rows affected). 이 조회로 뒤 트랜잭션을 앞 트랜잭션의 커밋 이후로 미뤄서,
     * 뒤 트랜잭션이 다시 조회했을 때 정상적으로 CAMPAIGN_NOT_FOUND(404)를 받게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> findByIdForUpdate(@Param("id") Long id);
}

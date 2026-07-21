package com.shinhan.klljs.domain.campaign.entity;

import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_unit_id", nullable = false)
    private MediaUnit mediaUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy; // 캠페인을 등록한 사용자(팀장)

    @Column(name = "campaign_name", nullable = false, length = 200)
    private String campaignName;

    @Column(name = "brand_name", nullable = false, length = 200)
    private String brandName;

    @Column(name = "execution_start_date", nullable = false)
    private LocalDate executionStartDate;

    @Column(name = "execution_end_date", nullable = false)
    private LocalDate executionEndDate;

    @Column(name = "daily_target_play_count", nullable = false)
    private Integer dailyTargetPlayCount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 업로드 단계에서 사용자가 선언하고 서명 토큰으로 전달된 소재 유형이다.
     * 실제 S3 객체의 Content-Type을 신뢰하거나 파일 바이트를 재검사하지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "creative_type", nullable = false, length = 20)
    private CampaignCreativeType creativeType;

    /**
     * 공개 URL 전체가 아니라 서버가 생성한 S3 object key만 저장한다.
     * 응답 URL은 배포 환경별 public base URL과 이 값을 조합해 생성한다.
     */
    @Column(name = "creative_storage_key", nullable = false, length = 1024)
    private String creativeStorageKey;

    /** 사용자가 업로드한 원본 파일명으로, 화면 표시와 추적 목적에만 사용한다. */
    @Column(name = "creative_original_filename", nullable = false, length = 255)
    private String creativeOriginalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CampaignStatus status;

    @Column(name = "registration_failure_reason", length = 1000)
    private String registrationFailureReason;

    @Builder
    public Campaign(Team team, MediaUnit mediaUnit, User createdBy, String campaignName, String brandName,
                     LocalDate executionStartDate, LocalDate executionEndDate, Integer dailyTargetPlayCount,
                     String description, CampaignCreativeType creativeType, String creativeStorageKey,
                     String creativeOriginalFilename, CampaignStatus status) {
        if (executionEndDate.isBefore(executionStartDate)) {
            throw new IllegalArgumentException("executionEndDate must not be before executionStartDate");
        }
        this.team = team;
        this.mediaUnit = mediaUnit;
        this.createdBy = createdBy;
        this.campaignName = campaignName;
        this.brandName = brandName;
        this.executionStartDate = executionStartDate;
        this.executionEndDate = executionEndDate;
        this.dailyTargetPlayCount = dailyTargetPlayCount;
        this.description = description;
        this.creativeType = creativeType;
        this.creativeStorageKey = creativeStorageKey;
        this.creativeOriginalFilename = creativeOriginalFilename;
        this.status = status;
    }


    public void markRegistered() {
        this.status = CampaignStatus.REGISTERED;
        this.registrationFailureReason = null;
    }

    public void markRegistrationFailed(String reason) {
        this.status = CampaignStatus.REGISTRATION_FAILED;
        this.registrationFailureReason = reason;
    }

    public void changeStatus(CampaignStatus status) {
        this.status = status;
    }

    public void rename(String campaignName) {
        this.campaignName = campaignName;
    }
}

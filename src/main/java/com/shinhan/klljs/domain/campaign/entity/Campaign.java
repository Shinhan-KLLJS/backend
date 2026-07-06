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
    @JoinColumn(name = "media_unit_id")
    private MediaUnit mediaUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy; // 캠페인을 등록한 사용자(팀장)

    @Column(name = "campaign_name", nullable = false, length = 200)
    private String campaignName;

    @Column(name = "execution_start_date", nullable = false)
    private LocalDate executionStartDate;

    @Column(name = "execution_end_date", nullable = false)
    private LocalDate executionEndDate;

    @Column(name = "daily_target_play_count", nullable = false)
    private Integer dailyTargetPlayCount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CampaignStatus status;

    @Column(name = "registration_failure_reason", length = 1000)
    private String registrationFailureReason;

    @Builder
    public Campaign(Team team, MediaUnit mediaUnit, User createdBy, String campaignName,
                     LocalDate executionStartDate, LocalDate executionEndDate, Integer dailyTargetPlayCount,
                     String description, String imageUrl, CampaignStatus status) {
        if (executionEndDate.isBefore(executionStartDate)) {
            throw new IllegalArgumentException("executionEndDate must not be before executionStartDate");
        }
        this.team = team;
        this.mediaUnit = mediaUnit;
        this.createdBy = createdBy;
        this.campaignName = campaignName;
        this.executionStartDate = executionStartDate;
        this.executionEndDate = executionEndDate;
        this.dailyTargetPlayCount = dailyTargetPlayCount;
        this.description = description;
        this.imageUrl = imageUrl;
        this.status = status;
    }

    public void assignMediaUnit(MediaUnit mediaUnit) {
        this.mediaUnit = mediaUnit;
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
}

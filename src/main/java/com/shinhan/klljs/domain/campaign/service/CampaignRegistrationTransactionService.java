package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.config.CampaignCreativeProperties;
import com.shinhan.klljs.domain.campaign.dto.CampaignRegistrationResponse;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.exception.MediaErrorCode;
import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.exception.UserErrorCode;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 팀과 매체 잠금을 일정한 순서로 획득해 기간이 겹치는 캠페인의 동시 등록을 막는다.
 *
 * <p>잠금 순서는 항상 team -> media unit이다. 서로 다른 팀이 같은 매체를 동시에 선택해도
 * media unit 잠금에서 직렬화되고, 뒤 트랜잭션은 앞 트랜잭션이 저장한 캠페인을 확인해 409를 반환한다.</p>
 */
@Service
@RequiredArgsConstructor
public class CampaignRegistrationTransactionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MediaUnitRepository mediaUnitRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final CampaignCreativeProperties creativeProperties;
    private final Clock clock;

    @Transactional
    public CampaignRegistrationResponse register(
            Long requesterId,
            Long teamId,
            CampaignRegistrationCommand command
    ) {
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_NOT_FOUND));

        // MEMBER를 포함한 모든 ACTIVE 멤버가 캠페인을 등록할 수 있다 - 역할 제한 없음.
        teamMemberRepository.findByUserIdAndTeamIdAndStatus(requesterId, teamId, TeamMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_ACCESS_DENIED));
        // 비회원에게 팀의 비활성 상태를 먼저 노출하지 않는다. ACTIVE 멤버만 팀 상태를 확인할 수 있다.
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new GeneralException(TeamErrorCode.TEAM_NOT_ACTIVE);
        }

        // 팀 검증이 끝난 뒤에만 매체를 잠가 모든 등록 경로의 lock order를 동일하게 유지한다.
        MediaUnit mediaUnit = mediaUnitRepository.findByIdForUpdate(command.mediaUnitId())
                .orElseThrow(() -> new GeneralException(MediaErrorCode.MEDIA_UNIT_NOT_FOUND));
        if (mediaUnit.getStatus() != MediaUnitStatus.ACTIVE) {
            throw new GeneralException(MediaErrorCode.MEDIA_UNIT_NOT_ACTIVE);
        }

        long conflicts = campaignRepository.countPeriodConflicts(
                mediaUnit.getId(),
                CampaignStatus.REGISTRATION_FAILED,
                command.executionStartDate(),
                command.executionEndDate()
        );
        if (conflicts > 0) {
            throw new GeneralException(CampaignErrorCode.CAMPAIGN_PERIOD_CONFLICT);
        }

        User createdBy = userRepository.findById(requesterId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        LocalDate today = LocalDate.now(clock.withZone(KST));
        CampaignStatus initialStatus = CampaignStatusReconciliationService.statusFor(
                command.executionStartDate(), command.executionEndDate(), today
        );
        Campaign campaign = Campaign.builder()

                .team(team)
                .mediaUnit(mediaUnit)
                .createdBy(createdBy)
                .campaignName(command.campaignName())
                .brandName(command.brandName())
                .executionStartDate(command.executionStartDate())
                .executionEndDate(command.executionEndDate())
                .dailyTargetPlayCount(command.dailyTargetPlayCount())
                .description(command.description())
                .creativeType(command.creativeType())
                .creativeStorageKey(command.creativeStorageKey())
                .creativeOriginalFilename(command.creativeOriginalFilename())
                .status(initialStatus)
                .build();
        campaignRepository.save(campaign);

        String creativeUrl = creativeProperties.publicBaseUrl() + "/" + command.creativeStorageKey();
        return CampaignRegistrationResponse.from(campaign, creativeUrl);
    }
}

package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamBusinessRegistration;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamBusinessRegistrationRepository;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 팀·OWNER·사업자등록을 <b>한 트랜잭션으로</b> 만든다 (docs/team-creation-api-spec.md 5절).
 * 셋 중 하나라도 실패하면 전체 롤백된다 - OWNER 없는 팀이나 사업자등록 없는 팀이 남지 않는다.
 *
 * <h3>여기엔 teams 행 잠금이 필요 없다</h3>
 * 팀을 <b>이 트랜잭션 안에서 INSERT</b>하므로 잠글 기존 행이 없다.
 * 동시 요청이 와도 서로 다른 팀이 만들어지고, team_business_registrations의 team_id UNIQUE 제약이
 * 자연히 "팀당 사업자등록 1건"을 지켜준다. 대신 "사용자당 팀 하나"를 지키기 위해 users 행을 잠근다.
 */
@Service
@RequiredArgsConstructor
public class TeamCreateWriteService {

    /** 개업일로 받아들이는 형식. OCR·프론트가 내려주는 "2024-06-24"와 "20240624" 둘 다 허용한다. */
    private static final List<DateTimeFormatter> OPENING_DATE_FORMATS =
            List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.BASIC_ISO_DATE);

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamBusinessRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public Team create(TeamCreateCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);

        // (1) 사용자 행 잠금. 반드시 이 트랜잭션의 첫 DB 읽기여야 한다.
        //     한 사용자의 동시 팀 생성을 한 줄로 세워, 팀이 두 개 생기는 것을 막는다.
        User owner = userRepository.findByIdForUpdate(command.userId())
                .orElseThrow(() -> new GeneralException(BusinessRegistrationErrorCode.NOT_TEAM_MEMBER));

        // (2) 잠금 이후의 첫 일반 조회 - 경쟁 트랜잭션이 방금 만든 팀도 여기서 보인다.
        //     사전 검사(TeamCreateService)는 빠른 실패일 뿐이고, 권위적인 판단은 여기서 한다.
        ensureHasNoTeam(command.userId());

        Team team = teamRepository.save(Team.builder()
                .teamName(command.teamName())
                .status(TeamStatus.ACTIVE)
                .build());

        // 팀을 만든 사람이 OWNER다. 초대로 들어온 게 아니므로 joinedViaInvite는 null이다.
        // 팀당 활성 OWNER 1명은 DB 유니크 인덱스(active_owner_marker)가 강제한다.
        teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .user(owner)
                .role(TeamMemberRole.OWNER)
                .status(TeamMemberStatus.ACTIVE)
                .joinedAt(now)
                .build());

        registrationRepository.save(registration(team, owner, command));

        return team;
    }

    /**
     * 사용자는 팀 하나에만 속한다. UserMeResponse가 teamId를 하나만 반환하는 것도 이 가정 위에 있어서,
     * 막지 않으면 두 번째 팀은 만들어지되 <b>사용자가 접근할 수 없는 유령 팀</b>이 된다.
     *
     * LEFT/REMOVED된 팀은 세지 않는다 - 팀을 나온 사람은 새 팀을 만들 수 있어야 한다.
     */
    private void ensureHasNoTeam(Long userId) {
        if (!teamMemberRepository.findTeamIdsByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE).isEmpty()) {
            throw new GeneralException(BusinessRegistrationErrorCode.ALREADY_HAS_TEAM);
        }
    }

    /**
     * 사용자가 화면에서 확인한 값을 그대로 저장한다. 진위확인·판정이 없으므로
     * <b>형식이 어긋나도 팀 생성을 막지 않는다</b> - 아래 정리 규칙으로 저장 가능한 형태만 맞춘다.
     */
    private TeamBusinessRegistration registration(Team team, User owner, TeamCreateCommand command) {
        return TeamBusinessRegistration.builder()
                .team(team)
                .uploadedBy(owner)
                .businessNumber(normalizeBusinessNumber(command.businessNumber()))
                .companyName(command.companyName())
                .representativeName(command.representativeName())
                // 업태·종목은 사용자가 보낸 값이 아니라 OCR 원본이다 (서명 토큰에서 꺼냈다).
                .businessType(command.businessType())
                .businessItem(command.businessItem())
                // 사업장 소재지는 이 플로우에서 받지 않는다 (화면에 없다).
                .businessAddress(null)
                .businessOpeningDate(parseOpeningDate(command.businessOpeningDate()))
                .documentStorageKey(command.documentS3Key())
                .build();
    }

    /**
     * 구분자(하이픈·공백)를 걷어낸 결과가 숫자 10자리면 그 형태로 통일해 저장한다.
     * 아니면 <b>트림한 원본을 그대로 저장한다</b> - OCR이 한 자리를 놓쳤어도 사용자가 확인한 값이고,
     * 여기서 400을 내면 화면에서 고칠 방법이 없는 사용자는 팀을 영영 못 만든다.
     *
     * {@code \p{Zs}}까지 걷어내는 이유: OCR·복사 경로로 NBSP(U+00A0)나 전각 공백(U+3000)이
     * 섞여 들어오는데, 자바의 {@code \s}는 ASCII 공백만 매칭해 이런 문자를 남긴다.
     */
    private static String normalizeBusinessNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[\\s\\p{Zs}\\-]", "");
        return digits.matches("\\d{10}") ? digits : Texts.trim(raw);
    }

    /**
     * 파싱에 실패하면 null을 저장한다 (컬럼이 NULL 허용이다). 400을 내지 않는 이유는
     * {@link #normalizeBusinessNumber}와 같다 - 확인 화면의 목적은 "그대로 생성"이지 재검증이 아니다.
     */
    private static LocalDate parseOpeningDate(String raw) {
        // Texts.trim은 strip()이 남기는 NBSP까지 걷어낸다 - 화면상 멀쩡해 보이는 날짜가
        // 보이지 않는 문자 하나 때문에 조용히 null로 저장되는 것을 막는다.
        String value = Texts.trim(raw);
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : OPENING_DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // 다음 형식으로 계속 시도한다.
            }
        }
        return null;
    }
}

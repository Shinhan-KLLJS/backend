package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.repository.TeamBusinessRegistrationRepository;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadTokenSigner;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

/**
 * 팀 생성은 팀·OWNER·사업자등록을 <b>한 트랜잭션으로</b> 만든다. 마지막 단계가 실패하면 앞의 두 개도
 * 남지 않아야 한다 (team-creation-api-spec.md 5절 필수 테스트 목록: "어느 저장 단계가 실패해도 전체 롤백").
 *
 * 롤백되지 않으면 <b>OWNER는 있는데 사업자등록이 없는 팀</b>이 남는다. 그 사용자는 "이미 팀이 있다"(409)에
 * 걸려 새 팀을 만들지도 못하고, 남은 팀은 사업자 정보 없이 존재하게 된다.
 *
 * <h3>이 테스트에 @Transactional을 걸지 않는 이유</h3>
 * 테스트에 @Transactional을 걸면 테스트 트랜잭션이 바깥을 감싸고 끝에서 무조건 롤백해버린다.
 * 그러면 "서비스의 트랜잭션이 스스로 롤백했는가"와 "테스트가 롤백해줬는가"를 구분할 수 없다 -
 * 검증하려는 것을 테스트 프레임워크가 대신 해버리는 셈이다. 그래서 실제로 커밋이 일어나게 두고,
 * 뒷정리는 직접 한다.
 */
// 시드 데이터가 카운트에 섞이면 단언이 흐려지므로 local-test-data를 끈다.
@SpringBootTest(properties = "app.local-test-data.enabled=false")
class TeamCreateRollbackTest {

    @Autowired
    private TeamCreateService teamCreateService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRegistrationUploadTokenSigner uploadTokenSigner;

    /** 마지막 저장 단계(사업자등록)를 실패시키기 위해 대역으로 바꾼다. */
    @MockitoBean
    private TeamBusinessRegistrationRepository registrationRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        this.userId = userRepository.save(User.builder()
                        .displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build())
                .getId();
    }

    @AfterEach
    void tearDown() {
        // 이 테스트는 진짜로 커밋하므로 뒷정리를 직접 한다.
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void create_rollsBackTeamAndOwnerWhenSavingTheRegistrationFails() {
        // 마지막 저장에서 DB가 터진 상황.
        willThrow(new RuntimeException("사업자등록 저장 실패"))
                .given(registrationRepository).save(any());

        assertThatThrownBy(() -> teamCreateService.create(userId, request()))
                .isInstanceOf(RuntimeException.class);

        // 앞 단계에서 만든 팀과 OWNER가 하나도 남지 않아야 한다.
        assertThat(teamRepository.count()).isZero();
        assertThat(teamMemberRepository.count()).isZero();
    }

    private TeamCreateRequest request() {
        return new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동", "495-92-40582", "2024-06-24",
                uploadTokenSigner.sign("team-registrations/doc.pdf", userId, "광고물제작", "광고대행"));
    }
}

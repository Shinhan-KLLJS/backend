package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.verification.AdvertisingClassification;
import com.shinhan.klljs.domain.team.verification.AdvertisingClassifier;
import com.shinhan.klljs.domain.team.verification.BusinessCheckResult;
import com.shinhan.klljs.domain.team.verification.BusinessInputNormalizer;
import com.shinhan.klljs.domain.team.verification.BusinessRegistrationVerifier;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationCommand;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationResult;
import com.shinhan.klljs.domain.team.verification.NormalizedBusinessInput;
import com.shinhan.klljs.domain.team.verification.VerificationDecision;
import com.shinhan.klljs.domain.team.verification.VerificationDecisionEvaluator;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 사업자등록증 검증의 전체 흐름을 조립한다 (server-verification-spec.md §1~§4).
 * 정규화 -> 국세청 조회 -> 광고업 분류 -> 최종 판정.
 *
 * <b>트랜잭션을 걸지 않는다.</b> 국세청 API 호출이 수 초 걸릴 수 있어 DB 커넥션을 그동안 붙잡으면
 * 검증과 무관한 요청까지 커넥션 풀을 기다리게 된다. DB 쓰기는 BusinessRegistrationWriteService가 맡는다.
 *
 * <b>HTTP 요청 DTO가 아니라 {@link BusinessVerificationCommand}를 받는다.</b> 재제출과 팀 생성이
 * 서로 다른 요청 DTO로 이 검증을 함께 쓰기 때문이다 - 어느 한쪽 DTO에 묶이면 다른 쪽이 재사용할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class BusinessVerificationService {

    /** 구체 벤더(국세청)가 아니라 포트에만 의존한다 - 기관이 바뀌어도 이 서비스는 그대로다. */
    private final BusinessRegistrationVerifier verifier;

    /** 개업일자가 미래인지 판단할 "오늘"을 얻는다. 테스트가 시간을 고정할 수 있도록 빈으로 주입받는다. */
    private final Clock clock;

    public BusinessVerificationResult verify(BusinessVerificationCommand command) {
        // 광고업 분류는 국세청과 무관하므로 입력 형식이 틀려도 항상 계산해둔다.
        // 그래야 review_required로 빠졌을 때도 사용자에게 매칭 근거를 함께 보여줄 수 있다.
        AdvertisingClassification classification =
                AdvertisingClassifier.classify(command.businessType(), command.businessItem());

        LocalDate todayKst = KstDateTimes.todayKst(LocalDateTime.now(clock));
        Optional<NormalizedBusinessInput> normalized = BusinessInputNormalizer.normalize(
                command.businessNumber(), command.businessOpeningDate(), command.representativeName(), todayKst);

        // 형식이 맞지 않으면 국세청 API를 호출하지 않는다. 쿼터만 쓰고 결과도 신뢰할 수 없기 때문이다.
        // check가 null이면 판정은 1번 규칙(VALIDATION_INPUT_INCOMPLETE)에서 멈춘다.
        BusinessCheckResult check = normalized.map(verifier::check).orElse(null);

        VerificationDecision decision = VerificationDecisionEvaluator.evaluate(check, classification);
        return new BusinessVerificationResult(normalized.orElse(null), classification, decision);
    }
}

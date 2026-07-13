package com.shinhan.klljs.domain.team.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 팀의 현재 사업자등록 정보 한 건 (team_id가 UNIQUE).
 *
 * 저장되는 값은 <b>OCR 추정값이 아니라 사용자가 화면에서 확인·수정해 확정 제출한 값</b>이다.
 * 개업일자는 진위확인에 필수라서, 저장해두어야 나중에 서버가 스스로 폐업 여부를 재검증할 때
 * 사용자에게 값을 다시 묻지 않아도 된다.
 */
@Entity
@Table(name = "team_business_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamBusinessRegistration extends BaseTimeEntity {

    /** rejection_reason 컬럼 길이. 판정 메시지가 이보다 길면 잘라서 저장한다. */
    private static final int MAX_REJECTION_REASON_LENGTH = 1000;

    /**
     * business_type / business_item 컬럼 길이.
     *
     * <b>이 두 값만 사용자 입력이 아니라 OCR이 읽어온 원문이다.</b> 요청 DTO를 거치지 않으니
     * @Size 검증도 받지 않는다 - OCR이 줄을 잘못 합쳐 200자짜리 업태를 뱉으면 그대로 여기까지 온다.
     * 막지 않으면 insert가 "Value too long for column BUSINESS_TYPE" 로 터지고,
     * <b>그 사용자는 팀을 영영 못 만든다</b> - 업태는 화면에 입력란이 없어 고칠 방법이 없고
     * 같은 문서를 다시 올려도 OCR이 같은 값을 뱉기 때문이다.
     *
     * 잘라서 저장한다. 광고업 분류는 자르기 전 원문으로 이미 끝났으므로 판정에는 영향이 없다.
     */
    private static final int MAX_BUSINESS_CLASSIFICATION_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, unique = true)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    @Column(name = "business_number", length = 20)
    private String businessNumber;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "representative_name", length = 100)
    private String representativeName;

    @Column(name = "business_type", length = 100)
    private String businessType; // 업태

    @Column(name = "business_item", length = 100)
    private String businessItem; // 종목. 업태와 함께 광고업 분류의 입력이 된다

    @Column(name = "business_address", length = 500)
    private String businessAddress; // 사업장 소재지

    @Column(name = "business_opening_date")
    private LocalDate businessOpeningDate; // 개업일

    @Column(name = "document_storage_key", nullable = false, length = 1024)
    private String documentStorageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * 아직 검증하지 않은 상태(PENDING)로 새 행을 만든다.
     * 판정 결과는 곧바로 {@link #applyDecision}이 덮어쓰므로 여기서 상태를 받지 않는다.
     */
    public static TeamBusinessRegistration pending(Team team, User uploadedBy, String documentStorageKey) {
        TeamBusinessRegistration registration = new TeamBusinessRegistration();
        registration.team = team;
        registration.uploadedBy = uploadedBy;
        registration.documentStorageKey = documentStorageKey;
        registration.verificationStatus = VerificationStatus.PENDING;
        return registration;
    }

    /**
     * 사용자가 확정 제출한 값으로 내용을 갱신한다. 판정 결과 반영은 {@link #applyDecision}이 따로 한다.
     * 재제출한 사람이 최초 등록자와 다를 수 있으므로 uploadedBy도 함께 갱신한다.
     */
    public void updateSubmission(User uploadedBy, String businessNumber, String companyName,
                                  String representativeName, String businessType, String businessItem,
                                  String businessAddress, LocalDate businessOpeningDate, String documentStorageKey) {
        this.uploadedBy = uploadedBy;
        this.businessNumber = businessNumber;
        this.companyName = companyName;
        this.representativeName = representativeName;
        // 업태·종목만 OCR 원문이라 길이 검증을 거치지 않고 들어온다 (위 상수 주석 참고).
        this.businessType = truncate(businessType, MAX_BUSINESS_CLASSIFICATION_LENGTH);
        this.businessItem = truncate(businessItem, MAX_BUSINESS_CLASSIFICATION_LENGTH);
        this.businessAddress = businessAddress;
        this.businessOpeningDate = businessOpeningDate;
        this.documentStorageKey = documentStorageKey;
    }

    /**
     * 검증 판정을 반영한다 (server-verification-spec.md §5.2).
     * APPROVED일 때만 verified_at을 채우고, 그 외에는 판정 메시지를 rejection_reason에 남긴다.
     * PENDING(review_required)도 사유가 필요하므로 REJECTED와 같은 자리에 메시지를 넣는다.
     */
    public void applyDecision(VerificationStatus status, String message, LocalDateTime verifiedAt) {
        this.verificationStatus = status;
        if (status == VerificationStatus.APPROVED) {
            this.verifiedAt = verifiedAt;
            this.rejectionReason = null;
        } else {
            this.verifiedAt = null;
            this.rejectionReason = truncate(message, MAX_REJECTION_REASON_LENGTH);
        }
    }

    public boolean isApproved() {
        return verificationStatus == VerificationStatus.APPROVED;
    }

    /** 컬럼 길이를 넘는 값은 잘라서 저장한다. 저장 실패로 요청 전체를 죽이는 것보다 낫다. */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

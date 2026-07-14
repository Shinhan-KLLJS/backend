package com.shinhan.klljs.domain.team.entity;

import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 팀의 사업자등록 정보 한 건 (team_id가 UNIQUE).
 *
 * 저장되는 값은 <b>OCR이 읽어 사용자가 화면에서 확인한 값</b>이다. 진위확인·자동 판정은
 * MVP에서 하지 않으므로 승인/반려 상태 개념이 없다 - 이 행이 존재한다는 것 자체가
 * "사용자가 사업자등록증을 제출하고 확인을 마쳤다"는 뜻이다.
 */
@Entity
@Table(name = "team_business_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamBusinessRegistration extends BaseTimeEntity {

    /**
     * business_type / business_item 컬럼 길이.
     *
     * <b>이 두 값만 사용자 입력이 아니라 OCR이 읽어온 원문이다.</b> 요청 DTO를 거치지 않으니
     * @Size 검증도 받지 않는다 - OCR이 줄을 잘못 합쳐 200자짜리 업태를 뱉으면 그대로 여기까지 온다.
     * 막지 않으면 insert가 "Value too long for column BUSINESS_TYPE" 로 터지고,
     * <b>그 사용자는 팀을 영영 못 만든다</b> - 업태는 화면에 입력란이 없어 고칠 방법이 없고
     * 같은 문서를 다시 올려도 OCR이 같은 값을 뱉기 때문이다. 그래서 잘라서 저장한다.
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
    private String businessType; // 업태 (OCR 원문)

    @Column(name = "business_item", length = 100)
    private String businessItem; // 종목 (OCR 원문)

    @Column(name = "business_address", length = 500)
    private String businessAddress; // 사업장 소재지

    @Column(name = "business_opening_date")
    private LocalDate businessOpeningDate; // 개업일

    @Column(name = "document_storage_key", nullable = false, length = 1024)
    private String documentStorageKey;

    @Builder
    private TeamBusinessRegistration(Team team, User uploadedBy, String businessNumber, String companyName,
                                     String representativeName, String businessType, String businessItem,
                                     String businessAddress, LocalDate businessOpeningDate,
                                     String documentStorageKey) {
        this.team = team;
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

    /** 컬럼 길이를 넘는 값은 잘라서 저장한다. 저장 실패로 요청 전체를 죽이는 것보다 낫다. */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

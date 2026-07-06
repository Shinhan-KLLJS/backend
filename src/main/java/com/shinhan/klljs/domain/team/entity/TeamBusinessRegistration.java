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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_business_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamBusinessRegistration extends BaseTimeEntity {

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

    @Column(name = "document_storage_key", nullable = false, length = 1024)
    private String documentStorageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Builder
    public TeamBusinessRegistration(Team team, User uploadedBy, String businessNumber, String companyName,
                                     String representativeName, String documentStorageKey,
                                     VerificationStatus verificationStatus) {
        this.team = team;
        this.uploadedBy = uploadedBy;
        this.businessNumber = businessNumber;
        this.companyName = companyName;
        this.representativeName = representativeName;
        this.documentStorageKey = documentStorageKey;
        this.verificationStatus = verificationStatus;
    }

    public void resubmit(String businessNumber, String companyName, String representativeName,
                          String documentStorageKey) {
        this.businessNumber = businessNumber;
        this.companyName = companyName;
        this.representativeName = representativeName;
        this.documentStorageKey = documentStorageKey;
        this.verificationStatus = VerificationStatus.PENDING;
        this.rejectionReason = null;
        this.verifiedAt = null;
    }

    public void approve(LocalDateTime verifiedAt) {
        this.verificationStatus = VerificationStatus.APPROVED;
        this.verifiedAt = verifiedAt;
        this.rejectionReason = null;
    }

    public void reject(String rejectionReason) {
        this.verificationStatus = VerificationStatus.REJECTED;
        this.rejectionReason = rejectionReason;
    }
}

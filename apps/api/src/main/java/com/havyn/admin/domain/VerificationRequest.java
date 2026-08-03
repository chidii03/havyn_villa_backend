package com.havyn.admin.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Host identity/KYC verification — see project-docs/prompts/18-admin-platform.md and
 * security/01-security-plan.md's "Host verification workflow (VerificationRequest)
 * with admin review; store minimal PII, restrict access, audit all access." Storing
 * only a {@code documentUrl} (wherever the host uploaded their ID — no new Cloudinary
 * integration added here for a single URL field) plus free-text notes is the
 * "minimal PII" this document is stored as; access is restricted to the owning user
 * and admins only (see {@code VerificationService}), and every admin review action is
 * audit-logged (see {@code AdminVerificationController}).
 */
@Entity
@Table(name = "verification_request")
public class VerificationRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "document_url", nullable = false)
    private String documentUrl;

    @Column(name = "notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "review_notes")
    private String reviewNotes;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected VerificationRequest() {
        // JPA
    }

    public VerificationRequest(UUID userId, String documentUrl, String notes) {
        this.userId = userId;
        this.documentUrl = documentUrl;
        this.notes = notes;
    }

    public void approve(UUID adminId, Instant when) {
        this.status = VerificationStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = when;
    }

    public void reject(UUID adminId, String reviewNotes, Instant when) {
        this.status = VerificationStatus.REJECTED;
        this.reviewNotes = reviewNotes;
        this.reviewedBy = adminId;
        this.reviewedAt = when;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public String getNotes() {
        return notes;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}

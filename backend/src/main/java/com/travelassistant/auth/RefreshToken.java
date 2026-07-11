package com.travelassistant.auth;

import com.travelassistant.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "family_id", length = 36, nullable = false)
    private String familyId;
    @Column(name = "replaced_by_token_id", length = 36)
    private String replacedByTokenId;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {}

    public RefreshToken(User user, String tokenHash, String familyId, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void initialize() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public void revoke(Instant now, String replacementId) {
        if (revokedAt == null) revokedAt = now;
        if (replacementId != null && replacedByTokenId == null) replacedByTokenId = replacementId;
    }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
    public boolean isRevoked() { return revokedAt != null; }
    public String getId() { return id; }
    public User getUser() { return user; }
    public String getFamilyId() { return familyId; }
    public String getReplacedByTokenId() { return replacedByTokenId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}

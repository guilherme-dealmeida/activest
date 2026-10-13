package com.primevest.domain.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.primevest.domain.ceilingprice.CeilingPrice;
import com.primevest.domain.goal.Goal;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.strategy.Strategy;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** Celular em E.164 (+5541999999999). Opcional e único; contato, não login (D6). */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * CPF cifrado (AES-256-GCM, o id do usuário como dado associado) — ver UserCpfService na
     * app-api. Os quatro campos andam juntos (ck_users_cpf_completo). O hash dá a unicidade.
     */
    @Column(name = "cpf_ciphertext")
    private byte[] cpfCiphertext;

    @Column(name = "cpf_iv")
    private byte[] cpfIv;

    @Column(name = "cpf_key_version")
    private Short cpfKeyVersion;

    @Column(name = "cpf_hash", length = 64)
    private String cpfHash;

    /** Quando o e-mail foi confirmado (código por e-mail ou conta Google). */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /** Token emitido antes disto é recusado: trocar a senha derruba as outras sessões (D15). */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    /** Versão dos termos aceita; diferente de {@link LegalTerms#CURRENT_VERSION} pede novo aceite (D19). */
    @Column(name = "terms_version", length = 30)
    private String termsVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 10)
    @Builder.Default
    private UserTheme theme = UserTheme.DARK;

    /** Acesso administrativo: rotinas da adm-api, conferência do extrato e as notificações de administrador. */
    @Column(name = "is_admin", nullable = false)
    @Builder.Default
    private boolean isAdmin = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Portfolio> portfolios = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Strategy> strategies = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CeilingPrice> ceilingPrices = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Goal> goals = new ArrayList<>();

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public boolean hasCpf() {
        return cpfHash != null;
    }

    /** Aceitou a versão atual dos termos de uso e da política de privacidade. */
    public boolean termsUpToDate() {
        return LegalTerms.CURRENT_VERSION.equals(termsVersion);
    }

    public void acceptTerms(LocalDateTime when) {
        this.termsAcceptedAt = when;
        this.termsVersion = LegalTerms.CURRENT_VERSION;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

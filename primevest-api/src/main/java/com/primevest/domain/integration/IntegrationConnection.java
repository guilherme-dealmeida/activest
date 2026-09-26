package com.primevest.domain.integration;

import com.primevest.domain.user.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Conexao de um usuario com uma fonte externa: uma por conta na fonte.
 *
 * <p>Pertence ao usuario, nunca ao servidor (P1 do plano). A credencial fica
 * em {@link IntegrationCredential}, que aponta para ca; o lado inverso nao e
 * mapeado de proposito — {@code @OneToOne} inverso nao e preguicoso, e a
 * conexao e carregada muito mais vezes do que o segredo precisa ser lido.</p>
 */
@Entity
@Table(name = "integration_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationProvider provider;

    /** Apelido dado pelo usuario ("Binance principal"). Unico por usuario. */
    @Column(nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ConnectionStatus status = ConnectionStatus.ACTIVE;

    /** Quando o usuario aceitou o texto de consentimento (LGPD, 6.4). */
    @Column(name = "consent_accepted_at", nullable = false)
    private LocalDateTime consentAcceptedAt;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

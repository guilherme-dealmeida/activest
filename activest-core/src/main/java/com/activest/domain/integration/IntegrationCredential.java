package com.activest.domain.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A credencial cifrada de uma conexao — a unica tabela com segredo.
 *
 * <p>Nunca e montada nem lida fora do
 * {@link com.activest.domain.integration.credential.CredentialVault}: e ele
 * que cifra, decifra e calcula o {@code fingerprint}. Nenhum DTO expoe esta
 * entidade, e ela nao tem {@code toString} (Lombok {@code @Data} ficou de fora
 * de proposito, para o segredo cifrado nao ir parar em log).</p>
 */
@Entity
@Table(name = "integration_credentials")
@Getter
@Setter
@NoArgsConstructor
public class IntegrationCredential {

    @Id
    @Column(name = "connection_id")
    private UUID connectionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id")
    private IntegrationConnection connection;

    /** JSON da credencial cifrado com AES-256-GCM (inclui a tag de 16 bytes). */
    @Column(nullable = false)
    private byte[] ciphertext;

    /** 12 bytes, novo a cada cifragem. */
    @Column(nullable = false)
    private byte[] iv;

    @Column(name = "key_version", nullable = false)
    private Short keyVersion;

    /** Os ultimos 4 caracteres da chave de API: o que a tela mostra. */
    @Column(nullable = false, length = 8)
    private String fingerprint;

    /** JSON da ultima checagem de permissao na fonte (6.4). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String permissions;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

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

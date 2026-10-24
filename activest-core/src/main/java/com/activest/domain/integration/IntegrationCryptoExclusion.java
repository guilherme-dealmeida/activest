package com.activest.domain.integration;

import com.activest.domain.asset.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Operacao de cripto importada que o usuario excluiu (D9, regra E2).
 *
 * <p>A sincronizacao seguinte encontra a operacao na corretora, consulta esta
 * tabela pelo par {@code (connection, externalId)} e nao a recria. "Restaurar"
 * (E4) apaga a linha, e a proxima sincronizacao traz a operacao de volta.</p>
 *
 * <p>Uma tabela por dominio: a renda fixa tera
 * {@code integration_fixed_income_exclusions}, se o Open Finance precisar.</p>
 */
@Entity
@Table(name = "integration_crypto_exclusions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationCryptoExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private IntegrationConnection connection;

    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /** A operacao como estava ao ser excluida: para listar e para restaurar. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_snapshot", nullable = false, columnDefinition = "jsonb")
    private String sourceSnapshot;

    @Column(name = "excluded_at", nullable = false, updatable = false)
    private LocalDateTime excludedAt;

    @PrePersist
    protected void onCreate() {
        if (this.excludedAt == null) {
            this.excludedAt = LocalDateTime.now();
        }
    }
}

package com.activest.domain.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Papel listado no exterior — Stock, REIT ou ETF internacional (V15, docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, I6):
 * 1:1 com {@code assets}, como {@code asset_terms} e {@code investment_funds}. Identidade (C5): o ativo vivo é casado
 * pelo {@code compositeFigi}; o ETF também pela série da SEC; a ação também pelo CIK.
 *
 * <p>As origens ({@code typeSource}, {@code categorySource}, {@code tickerSource}) dizem de onde veio cada
 * classificação; {@code CURATION} é a curadoria da planilha (C9), que a tarefa nunca sobrescreve.</p>
 */
@Entity
@Table(name = "foreign_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForeignListing {

    /** O ativo ({@code assets.id}). */
    @Id
    @Column(name = "asset_id")
    private Long assetId;

    /** Bolsa de listagem; nula no deslistado sem bolsa conhecida. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ForeignExchange exchange;

    /** Empresa (ação) ou trust (ETF) na SEC, sem os zeros à esquerda. */
    @Column(length = 10)
    private String cik;

    /** Série do fundo na SEC ({@code S000…}), só ETF. */
    @Column(name = "sec_series_id", length = 10)
    private String secSeriesId;

    @Column(name = "composite_figi", length = 12)
    private String compositeFigi;

    /** {@code securityType} do OpenFIGI (Common Stock, REIT, ETP...). */
    @Column(name = "figi_security_type", length = 40)
    private String figiSecurityType;

    @Column(name = "sic_code", length = 4)
    private String sicCode;

    /** OPENFIGI, LIST (marca de ETF ou preferencial da lista da bolsa), SEC_NAREIT ou CURATION. */
    @Column(name = "type_source", length = 12)
    private String typeSource;

    /** BDR, SIC, NAREIT, ETF_RULE ou CURATION. */
    @Column(name = "category_source", length = 12)
    private String categorySource;

    /** LIST, XBRL, NAREIT, FILING_TEXT, FUND_SERIES ou CURATION. */
    @Column(name = "ticker_source", length = 12)
    private String tickerSource;

    @Column(name = "first_seen_on")
    private LocalDate firstSeenOn;

    /** Último dia em que o papel estava na lista da bolsa (sumiço + 30 dias = saída, C7). */
    @Column(name = "last_seen_on")
    private LocalDate lastSeenOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Origem da curadoria da planilha (C9): nunca sobrescrita pela tarefa. */
    public static final String CURATION = "CURATION";

    @PrePersist
    void created() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() {
        updatedAt = LocalDateTime.now();
    }
}

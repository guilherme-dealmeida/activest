package com.activest.domain.fund;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cadastro CVM de um fundo de investimento, 1:1 com {@code assets} (V12, docs/planos/PLANO_FUNDOS_INVESTIMENTO.md).
 *
 * <p>O ativo do fundo tem {@code ticker} = CNPJ sem pontuação (ou {@code CNPJ-ID_SUBCLASSE}), que é chave e endereço e
 * nunca aparece como título; o título das telas é {@link #shortName} (D3). {@code assets.name} guarda a denominação
 * social atual.</p>
 */
@Entity
@Table(name = "investment_funds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentFund {

    @Id
    @Column(name = "asset_id")
    private Long assetId;

    /** CNPJ da classe (ou do fundo, no formato antigo) sem pontuação: 14 caracteres, alfanumérico desde 07/2026. */
    @Column(nullable = false, length = 14)
    private String cnpj;

    /** ID_SUBCLASSE da CVM; nulo na classe. */
    @Column(name = "subclass_id", length = 20)
    private String subclassId;

    /** Ativo da classe-mãe, quando este é uma subclasse. */
    @Column(name = "parent_asset_id")
    private Long parentAssetId;

    /** Rótulo das telas: denominação comercial vigente (senão a social) abreviada ({@link FundLabel}). */
    @Column(name = "short_name", nullable = false, length = 160)
    private String shortName;

    /** Denominação comercial vigente (cad_fi_hist); nula quando a CVM não tem. */
    @Column(name = "trade_name", length = 200)
    private String tradeName;

    /** FIF, FI, FAPI... */
    @Column(name = "fund_kind", length = 20)
    private String fundKind;

    @Column(name = "cvm_classification", length = 40)
    private String cvmClassification;

    @Column(name = "anbima_classification", length = 80)
    private String anbimaClassification;

    @Column(name = "target_audience", length = 20)
    private String targetAudience;

    private Boolean exclusive;

    @Column(name = "open_ended")
    private Boolean openEnded;

    /** Tributação de longo prazo (registro_classe): decide o regime de imposto do fundo (C8). */
    @Column(name = "long_term_tax")
    private Boolean longTermTax;

    @Column(name = "manager_name", length = 200)
    private String managerName;

    @Column(name = "manager_cnpj", length = 14)
    private String managerCnpj;

    @Column(name = "administrator_name", length = 200)
    private String administratorName;

    @Column(name = "administrator_cnpj", length = 14)
    private String administratorCnpj;

    @Column(name = "cvm_status", length = 40)
    private String cvmStatus;

    @Column(name = "cvm_status_since")
    private LocalDate cvmStatusSince;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "net_worth", precision = 20, scale = 2)
    private BigDecimal netWorth;

    @Column(name = "net_worth_date")
    private LocalDate netWorthDate;

    /** Data da última cota informada no informe diário. */
    @Column(name = "last_quote_on")
    private LocalDate lastQuoteOn;

    /** CNPJ e nomes (atuais e anteriores) normalizados para a busca (índice pg_trgm, D25). */
    @Column(name = "search_text", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String searchText = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    /** O ticker do ativo: CNPJ, ou CNPJ-ID_SUBCLASSE na subclasse. */
    public static String ticker(String cnpj, String subclassId) {
        return subclassId == null || subclassId.isBlank() ? cnpj : cnpj + "-" + subclassId.trim();
    }
}

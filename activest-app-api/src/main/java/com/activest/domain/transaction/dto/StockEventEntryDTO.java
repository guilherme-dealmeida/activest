package com.activest.domain.transaction.dto;

import com.activest.domain.corporateaction.StockEventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um evento do papel (bonificação, desdobramento ou grupamento) que mexeu numa posição do usuário — a linha do evento
 * no extrato de Transações (docs/planos/PLANO_EVENTOS_TRANSACOES.md, D1 e D11).
 *
 * <p>Não é transação: é calculado na leitura pelo mesmo percurso da Carteira, não se edita nem se exclui. A posição é a
 * da corretora (a bonificação entra sem custo); o custo do IR vem à parte (seção 8.4 do plano). Os campos do
 * ativo são os mesmos de {@link TransactionResponseDTO}, para a tela filtrar e mostrar as duas linhas do mesmo jeito.</p>
 */
public record StockEventEntryDTO(
    UUID portfolioAssetId,
    /** id em asset_stock_events: o "Informar custo" da bonificação. */
    Long stockEventId,
    String ticker,
    String assetName,
    Long assetTypeId,
    /** Rótulo do tipo de ativo (description), como em TransactionResponseDTO. */
    String assetTypeName,
    Long assetCategoryId,
    String assetCategoryName,
    Boolean hasLogo,
    Long issuerId,

    StockEventType type,
    /** Como a B3 publica: % de ações novas (desdobramento, bonificação) ou multiplicador (grupamento). */
    BigDecimal factor,
    /** Quantas cotas cada cota vira (desdobramento 300 → 4; grupamento 0,1; bonificação 1 → 1,01). */
    BigDecimal ratio,
    LocalDate dataCom,
    /** Data ex: o pregão seguinte à data-com, quando o evento passa a valer (D3). É a data da linha. */
    LocalDate exDate,
    /** Fonte do evento no catálogo: B3 ou MASSIVE (EUA). */
    String source,

    BigDecimal sharesBefore,
    BigDecimal sharesAfter,
    /** Variação com sinal (D4): +3, +900, −95. */
    BigDecimal sharesDelta,
    /** Fração que não virou cota — a B3 vende em leilão e paga em dinheiro (D8). Zero sem fração. */
    BigDecimal fractionShares,

    /**
     * Variação do custo da posição como a corretora, em reais (2 casas): zero na bonificação (as ações novas entram sem
     * custo, D12 revista em 18/11/2026); no desdobramento/grupamento, a parte do custo que saiu com a fração (negativa).
     */
    BigDecimal costDelta,
    /** Custo por ação bonificada para o IR (catálogo ou informado); nulo fora da bonificação ou sem custo. */
    BigDecimal taxUnitCost,
    /** Custo das ações bonificadas para o IR (novas × custo, 2 casas); nulo sem custo ou fora da bonificação. */
    BigDecimal taxCostDelta,
    /** De onde veio o custo do IR da bonificação; nulo fora da bonificação. */
    TaxCostOrigin taxCostOrigin
) {

    /** Custo do IR da bonificação: do catálogo (a B3 informou), informado pelo usuário, ou ainda não informado. */
    public enum TaxCostOrigin {
        CATALOG,
        USER,
        MISSING
    }
}

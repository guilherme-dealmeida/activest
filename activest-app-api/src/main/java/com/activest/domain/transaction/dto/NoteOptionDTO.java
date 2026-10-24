package com.activest.domain.transaction.dto;

import com.activest.domain.asset.Indexer;
import com.activest.domain.asset.RateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Um titulo de renda fixa do usuario, para o seletor "Titulo" da Nova Transacao.
 *
 * <p>Vem de todos os titulos do ativo, inclusive os liquidados e os vencidos: um
 * lancamento retroativo pode cair numa data em que o titulo ainda rendia. A
 * carteira ({@code /portfolio/assets}) so traz as posicoes em aberto, e por isso
 * nao serve de lista aqui. Quem recusa o aporte fora de prazo e a data
 * ({@code TransactionService.validateBuyBeforeMaturity}).</p>
 *
 * @param settled a posicao do titulo esta liquidada (resgate total lancado)
 * @param matured o vencimento ja chegou (mesma regra do selo "Vencido" da carteira)
 * @param issuer  nome de exibicao da emissora (o do grupo, R10)
 */
public record NoteOptionDTO(
    UUID noteId,
    String issuer,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate,
    boolean settled,
    boolean matured,
    /** Emissora, distribuidora, codigo do papel e garantia (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md). */
    com.activest.domain.fixedincome.FixedIncomeIssuers.NoteInfo noteInfo
) {}

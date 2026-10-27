package com.activest.domain.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.activest.domain.asset.Indexer;
import com.activest.domain.asset.RateType;
import com.activest.domain.transaction.TransactionType;

/**
 * Criação de transação.
 *
 * <p>O ticker precisa existir em assets. O usuário escolhe o TIPO de ativo, que
 * restringe o autocomplete, e seleciona um ativo do catálogo — não há criação
 * de ativo por aqui: ticker desconhecido é erro, não cadastro.</p>
 *
 * <p>Os campos do título descrevem o PAPEL, não o ativo. Para renda fixa eles
 * viram uma linha em fixed_income_notes: dois CDBs de bancos diferentes são
 * duas notas, cada uma com seu emissor, vencimento e taxa. Renda variável
 * deixa todos nulos.</p>
 *
 * <p>{@code noteId} aponta para uma nota já existente, para lançar mais de uma
 * movimentação sobre o mesmo papel. Nulo numa compra de renda fixa significa
 * "papel novo": a nota nasce junto com a transação. No Tesouro Direto ele nunca
 * é informado: cada compra é um título novo (regra H) e a venda é do ativo, com
 * os títulos atribuídos por FIFO.</p>
 */
public record TransactionCreateDTO(
    @NotBlank String ticker,
    @NotNull TransactionType type,
    // Quantidade x preco para a maioria dos tipos; so o valor total para os
    // lancados por valor (Renda Fixa). Qual dos dois e obrigatorio depende do
    // tipo do ativo, que o DTO nao conhece: a regra vive no TransactionService.
    // Na VENDA de Renda Fixa, totalAmount e o LIQUIDO que o usuario recebeu: o
    // bruto, o IR e o IOF sao calculados pelos lotes (PLANO_VENDA_RENDA_FIXA, R3).
    @Positive BigDecimal shares,
    @Positive BigDecimal pricePerShare,
    @Positive BigDecimal totalAmount,
    BigDecimal brokerageFee,
    @NotNull LocalDate transactionDate,
    Boolean isReservation,

    /**
     * Venda de Renda Fixa: o usuário marcou "liquidação total".
     *
     * <p>Muda o que a venda significa. Marcada, ela encerra a aplicação e a
     * valorização é ajustada para que aplicado + valorização seja exatamente o
     * que foi resgatado. Desmarcada, é resgate parcial e sempre sobra posição.
     * Ignorado fora de Renda Fixa e fora de venda.</p>
     */
    Boolean isFullSettlement,

    // ----- dados do título (renda fixa) -----
    UUID noteId,
    /**
     * Emissora do título novo: id em fixed_income_issuers, de um tipo aceito pelo produto
     * (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R4). Ignorado no Tesouro (sempre o Tesouro Nacional).
     */
    Long issuerId,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate,
    /**
     * Distribuidora (onde foi comprado): obrigatória no título novo de renda fixa e na compra e na VENDA do Tesouro
     * — a venda do Tesouro consome só os títulos dela (R13).
     */
    Long distributorId,
    /** Código do papel na B3 (ex.: CDBA2478FYE), opcional (R8). */
    String paperCode,
    /**
     * Câmbio para reais (docs/planos/PLANO_CADASTRO_MOEDAS.md, D5/D16): só em ativo cotado em outra moeda — nele,
     * preço, total e taxa vêm na moeda do ativo. Nulo = PTAX de venda da data. Em ativo em reais é recusado se ≠ 1.
     */
    BigDecimal fxRate
) {
    /** A forma de antes da moeda: sem câmbio (ativo em reais, ou PTAX da data). */
    public TransactionCreateDTO(String ticker, TransactionType type, BigDecimal shares, BigDecimal pricePerShare,
                                BigDecimal totalAmount, BigDecimal brokerageFee, LocalDate transactionDate,
                                Boolean isReservation, Boolean isFullSettlement, UUID noteId, Long issuerId,
                                LocalDate maturityDate, RateType rateType, Indexer indexer, BigDecimal indexerRate,
                                Long distributorId, String paperCode) {
        this(ticker, type, shares, pricePerShare, totalAmount, brokerageFee, transactionDate, isReservation,
            isFullSettlement, noteId, issuerId, maturityDate, rateType, indexer, indexerRate, distributorId, paperCode,
            null);
    }
}

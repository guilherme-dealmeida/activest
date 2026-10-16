package com.primevest.domain.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.transaction.TransactionType;

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
    String issuer,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate
) {}

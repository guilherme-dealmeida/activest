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
 * "papel novo": a nota nasce junto com a transação.</p>
 */
public record TransactionCreateDTO(
    @NotBlank String ticker,
    @NotNull TransactionType type,
    // Quantidade x preco para a maioria dos tipos; so o valor total para os
    // lancados por valor (Renda Fixa). Qual dos dois e obrigatorio depende do
    // tipo do ativo, que o DTO nao conhece: a regra vive no TransactionService.
    @Positive BigDecimal shares,
    @Positive BigDecimal pricePerShare,
    @Positive BigDecimal totalAmount,
    BigDecimal brokerageFee,
    @NotNull LocalDate transactionDate,
    Boolean isReservation,

    // ----- dados do título (renda fixa) -----
    UUID noteId,
    String issuer,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate
) {}

package com.primevest.domain.transaction.dto;

import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.transaction.TransactionOrigin;
import com.primevest.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transação como as telas a leem.
 *
 * <p>{@code portfolioAssetId} existe para o filtro "Listar" da carteira: o
 * ticker não identifica a posição desde que ela passou a ser por título — três
 * CDBs são três posições, todas "CDB".</p>
 *
 * <p>Os campos de grupo descrevem a venda do usuário quando ela foi dividida
 * por título (Tesouro Direto). {@code groupShares} e {@code groupBrokerageFee}
 * são o que ele digitou; {@code shares} e {@code brokerageFee} são a parte
 * desta linha. A edição reabre o formulário com os valores do GRUPO, porque é
 * a venda inteira que se edita.</p>
 */
public record TransactionResponseDTO(
    UUID id,
    UUID portfolioAssetId,
    String ticker,
    String assetName,
    Long assetTypeId,
    String assetTypeName,
    /**
     * Categoria do ativo. Existe para o filtro da listagem: sem ela a tela teria
     * que buscar o catálogo inteiro para descobrir a categoria de cada linha.
     * Nula quando o ativo não tem categoria — o vínculo é opcional em assets.
     */
    Long assetCategoryId,
    /** Rótulo da categoria (getDisplayName), nunca a chave técnica. Vazio sem categoria. */
    String assetCategoryName,
    TransactionType type,
    BigDecimal shares,
    BigDecimal pricePerShare,
    BigDecimal totalAmount,
    BigDecimal brokerageFee,
    LocalDate transactionDate,
    LocalDateTime createdAt,

    // ----- venda dividida por titulo -----
    UUID saleGroupId,
    /** Posição desta linha dentro do grupo, a partir de 1. Nulo fora de grupo. */
    Integer groupPart,
    /** Quantas linhas o grupo tem. Nulo fora de grupo. */
    Integer groupParts,
    /** Quantidade e taxas da venda inteira. Iguais aos da linha fora de grupo. */
    BigDecimal groupShares,
    BigDecimal groupBrokerageFee,

    /** Venda de Renda Fixa marcada como liquidação total. */
    boolean isFullSettlement,

    /** Criptomoeda: a tela aceita e mostra quantidade e preco com ate 8 casas. */
    boolean isCrypto,

    /** Digitada (MANUAL) ou importada de uma corretora (AUTO) — o selo de origem da tela. */
    TransactionOrigin origin,
    /** Importada que o usuario editou (regra E1). Nulo = identica a corretora. */
    LocalDateTime editedAt,
    /** Apelido da conexao de onde veio ("Binance principal"). Nulo nas digitadas. */
    String connectionLabel,
    /** Corretora da conexao ("BINANCE"), para o selo de origem. Nulo nas digitadas. */
    String connectionProvider,

    // ----- dados do titulo -----
    UUID noteId,
    String issuer,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate,

    // ----- coluna ATIVO da listagem, igual a carteira -----
    /** null = tipo sem logo (renda fixa, Tesouro); senao, se ha arquivo de logo. */
    Boolean hasLogo,
    /** assets.issuer_id: com emissor, o ticker vira link para a tela de emissoras. */
    Long issuerId,

    // ----- venda de renda fixa e Tesouro (docs/planos/PLANO_VENDA_RENDA_FIXA.md) -----
    /**
     * Liquido da venda: o digitado na renda fixa, o calculado no Tesouro. Nulo fora de venda de RF/TD. Na venda de
     * renda fixa, {@code totalAmount} e o BRUTO calculado a partir dele.
     */
    BigDecimal netAmount,
    /** IR da venda (soma dos lotes/partes). */
    BigDecimal incomeTax,
    /** IOF da venda. */
    BigDecimal iofTax,
    /** Liquidacao total da renda fixa: bruto menos o saldo da curva (o "ajuste da liquidacao"). */
    BigDecimal settlementAdjustment,

    /**
     * Emissora e distribuidora do titulo (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md): ids, nome de exibicao, razao
     * social, regra de saida, codigo do papel, logo e garantia. {@code issuer} acima e o nome de exibicao da emissora.
     */
    com.primevest.domain.fixedincome.FixedIncomeIssuers.NoteInfo noteInfo
) {}

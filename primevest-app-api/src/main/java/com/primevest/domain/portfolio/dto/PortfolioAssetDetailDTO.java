package com.primevest.domain.portfolio.dto;

import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha da carteira.
 *
 * <p>issuer e maturityDate vêm do papel, e existem porque a carteira passou a
 * repetir ticker: duas aplicações em CDB são duas linhas, e sem o emissor e o
 * vencimento elas ficariam idênticas na tela. Nulos para renda variável.</p>
 *
 * <p>purchaseDate é a data de compra do título, e só vem preenchida no
 * Tesouro Direto: lá cada compra é um título novo (a taxa é do dia), e duas
 * linhas do mesmo papel se distinguem pela data em que foram aplicadas. É
 * também a ordem em que a venda consome os títulos (FIFO).</p>
 *
 * <p>rateType, indexer e indexerRate são a remuneração contratada na nota
 * ("110% do CDI", "IPCA + 6%"). Também nulos para renda variável.</p>
 *
 * <p>{@code totalInvested} tem significados diferentes por tipo, de propósito:
 * nos tipos por cota é o custo que ainda está na posição, já reduzido pelas
 * vendas; na Renda Fixa é o VALOR APLICADO, a soma das compras, e o que saiu
 * está em {@code totalSold}. A tela usa rótulos diferentes pelo mesmo motivo —
 * as regras E e F da renda fixa comparam o aplicado com o resgatado, e abater
 * um do outro tornaria as duas indecidíveis.</p>
 */
public record PortfolioAssetDetailDTO(
    UUID id,
    String ticker,
    String assetName,
    String typeName,
    String categoryName,
    UUID noteId,
    String issuer,
    LocalDate purchaseDate,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate,
    BigDecimal totalShares,
    BigDecimal averagePrice,
    BigDecimal totalInvested,
    /** Resgatado: só na Renda Fixa. Zero nos tipos por cota. */
    BigDecimal totalSold,
    BigDecimal currentValue,
    BigDecimal currentPrice,
    BigDecimal totalDividends,
    BigDecimal valorization,
    BigDecimal returnPercentage,
    boolean isReservation,
    boolean isSettled,
    /** Criptomoeda: a tela mostra quantidade e precos com ate 8 casas. */
    boolean isCrypto,
    /**
     * Logo guardado em carga/data/logos (F7). Nulo nos tipos sem logo (renda fixa,
     * Tesouro, fundos): a tela nao desenha nada. false = mostra as iniciais.
     */
    Boolean hasLogo,
    /**
     * Emissor do CATALOGO ({@code assets.issuer_id}: companhia ou FII), para o link da tela
     * de emissoras. Nulo sem emissor. Nao confundir com {@code issuer}, o emissor da nota de
     * renda fixa (o banco do CDB).
     */
    Long issuerId,
    /**
     * Base do valor atual na renda fixa (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, secao 5): CURVA (renda fixa
     * privada, taxa contratada), MERCADO (Tesouro, preco oficial) ou SEM_CURVA. Nulo fora da renda fixa.
     */
    String valuationBasis,
    /** Ate quando o valor vale: o ultimo CDI (curva) ou o dia do preco do Tesouro. */
    LocalDate valuationDate,
    /** Motivo de nao ter curva, ou a observacao da inflacao estimada. */
    String valuationNote,
    /** Passou do vencimento: na renda fixa privada, falta lancar o resgate. */
    boolean matured
) {}

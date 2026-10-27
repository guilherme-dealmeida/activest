package com.activest.domain.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.activest.domain.asset.Indexer;
import com.activest.domain.asset.RateType;

/**
 * Ativo do catálogo.
 *
 * <p>maturityDate, rateType e indexer vêm de {@code asset_terms} e são nulos
 * para tudo que não é Tesouro Direto. São os parâmetros PÚBLICOS do papel: o
 * front os usa para pré-preencher o título na hora do lançamento.</p>
 *
 * <p>Não existe indexerRate aqui. A taxa é da nota do usuário, nunca do
 * catálogo — no Tesouro ela depende do dia da compra, e num CDB depende do
 * banco. Enquanto a coluna existia em assets, vivia nula.</p>
 */
public record AssetResponseDTO(
    Long id,
    String ticker,
    String name,
    Long typeId,
    String typeName,
    Long categoryId,
    String categoryName,
    BigDecimal currentPrice,
    LocalDateTime priceUpdatedAt,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    boolean active,
    /** Moeda em que o ativo é cotado; {@code currentPrice} está nela (docs/planos/PLANO_CADASTRO_MOEDAS.md, D2/D11). */
    String currencyCode
) {}

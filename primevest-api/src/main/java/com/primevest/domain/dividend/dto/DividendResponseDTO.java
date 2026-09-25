package com.primevest.domain.dividend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.dividend.DividendType;

public record DividendResponseDTO(
    Long id,
    String ticker,
    String assetName,
    BigDecimal amountPerShare,
    /** Total BRUTO, truncado nos centavos. */
    BigDecimal totalAmount,
    LocalDate exDate,
    LocalDate paymentDate,
    DividendType type,
    /** Cotas da posicao na data-com (nulo no provento MANUAL antigo). */
    BigDecimal sharesOnDate,
    /** Aliquota de IR aplicada (0,175 = 17,5%; 0 = isento; nulo = sem regra). */
    BigDecimal withholdingRate,
    /** IR retido, truncado nos centavos. Nulo = sem regra. */
    BigDecimal withholdingTax,
    /** Valor liquido por cota, 8 casas — so exibicao. Nulo = sem regra. */
    BigDecimal netAmountPerShare,
    /** TOTAL liquido = totalAmount - withholdingTax (conta da B3). Nulo = sem regra. */
    BigDecimal netAmount,
    /** Base legal da regra aplicada. Nulo = sem regra. */
    String withholdingLegalBasis,
    Long assetTypeId,
    /** AssetType.getDescription(), para exibicao. */
    String assetTypeDescription,
    /** Mesma regra da carteira: null = tipo sem logo; false = mostra as iniciais. */
    Boolean hasLogo,
    /** Ja recebido hoje (fuso da B3)? false = a receber. Dividend.isReceived. */
    boolean received,
    /** Fonte do evento do catalogo (B3, B3_HISTORICO, FNET, EXTRATO_B3). EXTRATO_B3 = data-com deduzida. */
    String cashEventSource,
    /** Origem da data de pagamento quando nao e a da fonte: EXTRATO_B3 ou DEDUZIDA (V24). Nulo = oficial. */
    String paymentDateSource
) {}

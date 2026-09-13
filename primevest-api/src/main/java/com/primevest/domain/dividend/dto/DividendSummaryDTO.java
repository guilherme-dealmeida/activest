package com.primevest.domain.dividend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DividendSummaryDTO(
    BigDecimal totalDividends,
    Map<String, BigDecimal> byType,
    List<DividendResponseDTO> recent
) {}

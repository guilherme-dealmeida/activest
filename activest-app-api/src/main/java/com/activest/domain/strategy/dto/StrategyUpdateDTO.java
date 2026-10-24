package com.activest.domain.strategy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * A estrategia inteira, em uma requisicao: o PUT e full replace.
 *
 * <p>O {@code @Valid} na lista nao e enfeite. Sem ele o Bean Validation nao
 * desce nos itens, e os limites de cada percentual nunca eram aplicados: um
 * ideal de 150% passava direto para o banco.</p>
 */
public record StrategyUpdateDTO(
    @NotNull(message = "Informe os itens da estratégia")
    @Valid
    List<StrategyItemDTO> items
) {
    public record StrategyItemDTO(
        @NotNull(message = "Informe o tipo de ativo")
        Long assetTypeId,

        @NotNull(message = "Informe o percentual ideal")
        @DecimalMin(value = "0", message = "O percentual ideal não pode ser negativo")
        @DecimalMax(value = "100", message = "O percentual ideal não pode passar de 100%")
        BigDecimal targetPercentage
    ) {}
}

package com.primevest.domain.integration.dto;

import com.primevest.domain.integration.IntegrationProvider;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Conectar uma conta de corretora (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.7).
 *
 * <p>{@code apiSecret} so existe nesta requisicao: vai cifrado para o banco e
 * nenhuma resposta o devolve. O {@code toString} e mascarado pelo mesmo motivo.</p>
 */
public record ConnectionCreateDTO(
    @NotNull IntegrationProvider provider,
    @NotBlank @Size(max = 100) String label,
    @NotBlank @Size(max = 200) String apiKey,
    @NotBlank @Size(max = 200) String apiSecret,
    /** O usuario leu e aceitou o que sera lido, para que e por quanto tempo (6.4). */
    @NotNull @AssertTrue(message = "É preciso aceitar o consentimento para conectar.") Boolean consentAccepted
) {
    @Override
    public String toString() {
        return "ConnectionCreateDTO[provider=" + provider + ", label=" + label + ", apiKey=****, apiSecret=****]";
    }
}

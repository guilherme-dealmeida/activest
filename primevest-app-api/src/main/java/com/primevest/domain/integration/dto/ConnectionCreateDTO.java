package com.primevest.domain.integration.dto;

import com.primevest.domain.integration.IntegrationProvider;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Conectar uma conta numa fonte (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.7;
 * docs/planos/PLANO_INTEGRACAO_B3.md, G9). Cada fonte pede a sua credencial:
 *
 * <ul>
 *   <li><b>BINANCE</b> — {@code apiKey} e {@code apiSecret} (chave so de leitura);</li>
 *   <li><b>B3</b> — {@code documentNumber}, o CPF do investidor (com ou sem mascara).</li>
 * </ul>
 *
 * <p>A credencial so existe nesta requisicao: vai cifrada para o banco e
 * nenhuma resposta a devolve. O {@code toString} e mascarado pelo mesmo motivo.</p>
 */
public record ConnectionCreateDTO(
    @NotNull IntegrationProvider provider,
    @NotBlank @Size(max = 100) String label,
    @Size(max = 200) String apiKey,
    @Size(max = 200) String apiSecret,
    @Size(max = 20) String documentNumber,
    /** O usuario leu e aceitou o que sera lido, para que e por quanto tempo (6.4). */
    @NotNull @AssertTrue(message = "É preciso aceitar o consentimento para conectar.") Boolean consentAccepted
) {
    @Override
    public String toString() {
        return "ConnectionCreateDTO[provider=" + provider + ", label=" + label
            + ", apiKey=****, apiSecret=****, documentNumber=****]";
    }
}

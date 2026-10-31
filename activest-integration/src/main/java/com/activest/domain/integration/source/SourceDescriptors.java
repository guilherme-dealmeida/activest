package com.activest.domain.integration.source;

import com.activest.domain.integration.IntegrationProvider;
import com.activest.domain.integration.source.SourceDescriptor.CredentialKind;

import java.util.Arrays;
import java.util.List;

/**
 * Os descritores de todas as fontes que o Activest conhece (docs/planos/PLANO_INTEGRACOES.md, 10.1), ligadas ou nao
 * no servidor: a fonte desligada (B3 sem contrato) aparece no catalogo como indisponivel (D2).
 *
 * <p>Fonte nova: valor novo em {@link IntegrationProvider} (com a migration da constraint), o conector e o descritor
 * aqui — o {@code switch} sem {@code default} nao compila sem ele.</p>
 */
public final class SourceDescriptors {

    private SourceDescriptors() {
    }

    public static SourceDescriptor of(IntegrationProvider provider) {
        return switch (provider) {
            case BINANCE -> new SourceDescriptor(provider, "Binance", "Corretora de cripto e de ações dos EUA",
                "Criptomoedas (compras, vendas e conversões) e ações e ETFs americanos da Binance Stocks, com uma "
                    + "chave só de leitura.",
                CredentialKind.API_KEY, "/images/BINANCE.png", true, false);
            case B3 -> new SourceDescriptor(provider, "B3", "Bolsa brasileira",
                "Ações, FIIs, ETFs, BDRs e compras de Tesouro Direto: as negociações da Área do Investidor, pelo "
                    + "seu CPF.",
                CredentialKind.DOCUMENT, "/images/B3.png", false, true);
        };
    }

    /** Todas, na ordem do enum. */
    public static List<SourceDescriptor> all() {
        return Arrays.stream(IntegrationProvider.values()).map(SourceDescriptors::of).toList();
    }
}

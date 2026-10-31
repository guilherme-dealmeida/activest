package com.activest.domain.integration.dto;

import com.activest.domain.integration.IntegrationProvider;
import com.activest.domain.integration.source.SourceDescriptor;

/**
 * Uma fonte que o Activest conhece, para o catalogo da tela de Integracoes (docs/planos/PLANO_INTEGRACOES.md, 10.1).
 *
 * @param credential   o que a fonte pede para conectar: {@code API_KEY} (chave e segredo) ou {@code DOCUMENT} (CPF)
 * @param fiatDeposits a fonte informa depositos em dinheiro (total na pre-visualizacao)
 * @param dateOnly     a fonte entrega so a data do negocio (a tela nao mostra a hora)
 * @param oncePerDay   a fonte aceita uma consulta por dia para o mesmo investidor (B3)
 * @param available    ligada no servidor; desligada aparece no catalogo como indisponivel (D2)
 */
public record SourceDTO(IntegrationProvider provider, String name, String category, String description,
                        String credential, String logo, boolean fiatDeposits, boolean dateOnly, boolean oncePerDay,
                        boolean available) {

    public static SourceDTO of(SourceDescriptor d, boolean oncePerDay, boolean available) {
        return new SourceDTO(d.provider(), d.name(), d.category(), d.description(), d.credential().name(), d.logo(),
            d.fiatDeposits(), d.dateOnly(), oncePerDay, available);
    }
}

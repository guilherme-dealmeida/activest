package com.activest.domain.integration.source;

import com.activest.domain.integration.IntegrationProvider;

/**
 * Como uma fonte se apresenta na tela de Integracoes (docs/planos/PLANO_INTEGRACOES.md, 10.1): o catalogo de fontes e
 * o formulario de conexao saem daqui, nao de textos fixos no front.
 *
 * @param provider     a fonte
 * @param name         nome de exibicao ('Binance', 'B3')
 * @param category     o que a fonte e, em poucas palavras
 * @param description  o que o Activest importa dela
 * @param credential   o que ela pede para conectar
 * @param logo         caminho da imagem servida pelo front ('/images/BINANCE.png')
 * @param fiatDeposits a fonte informa depositos em dinheiro (a pre-visualizacao mostra o total)
 * @param dateOnly     a fonte entrega so a data do negocio, sem a hora (B3, B2)
 */
public record SourceDescriptor(IntegrationProvider provider, String name, String category, String description,
                               CredentialKind credential, String logo, boolean fiatDeposits,
                               boolean dateOnly) {

    /** O que a fonte pede para conectar. */
    public enum CredentialKind {
        /** Chave e segredo de API, so de leitura. */
        API_KEY,
        /** Documento do investidor (CPF), o do perfil do usuario. */
        DOCUMENT
    }
}

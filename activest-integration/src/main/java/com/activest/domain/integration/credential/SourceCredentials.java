package com.activest.domain.integration.credential;

/**
 * A credencial de uma conexao, do jeito que cada fonte pede
 * (docs/planos/PLANO_INTEGRACAO_B3.md, G1).
 *
 * <ul>
 *   <li>{@link ApiKeyCredentials} — chave + segredo do usuario (Binance e corretoras cripto);</li>
 *   <li>{@link InvestorDocument} — o CPF do investidor (B3). A credencial de acesso a API e da
 *   plataforma, fica no {@code .env}; do usuario o Activest so guarda quem ele e na fonte.</li>
 * </ul>
 *
 * <p>Gravada cifrada pelo {@link CredentialVault}, sempre na mesma {@code integration_credentials}.
 * {@code toString} mascarado em todas as implementacoes.</p>
 */
public sealed interface SourceCredentials permits ApiKeyCredentials, InvestorDocument {

    /** O pedaco que a tela pode mostrar para o usuario reconhecer a credencial. */
    String fingerprint();
}

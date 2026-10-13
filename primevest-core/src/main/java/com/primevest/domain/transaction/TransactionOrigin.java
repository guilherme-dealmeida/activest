package com.primevest.domain.transaction;

/**
 * De onde veio a transacao (V25, ck_transactions_origin). Mesmos valores de
 * {@link com.primevest.domain.dividend.DividendOrigin}.
 *
 * <ul>
 *   <li><b>MANUAL</b> — digitada pelo usuario. Sem {@code externalId} nem
 *   {@code sourceSnapshot}.</li>
 *   <li><b>AUTO</b> — importada de uma integracao. Nasce com {@code externalId}
 *   (unico por conexao) e com {@code sourceSnapshot}; pode ser editada, e ai
 *   ganha {@code editedAt} (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.10).</li>
 *   <li><b>SISTEMA</b> — criada pelo PrimeVest: a liquidacao automatica do titulo vencido (V50,
 *   docs/planos/PLANO_NOTIFICACOES.md, secao 6). Sem {@code externalId} nem snapshot; editada pelo usuario ganha
 *   {@code editedAt} e a rotina nao a refaz mais.</li>
 * </ul>
 */
public enum TransactionOrigin {
    MANUAL,
    AUTO,
    SISTEMA
}

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
 * </ul>
 */
public enum TransactionOrigin {
    MANUAL,
    AUTO
}

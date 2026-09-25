package com.primevest.domain.dividend;

/**
 * De onde veio o provento da posicao (V20).
 *
 * <ul>
 *   <li><b>AUTO</b> — criado pelo {@link DividendRecalculator} a partir de um
 *   evento do catalogo ({@link AssetCashEvent}) x cotas na data-com. Refeito
 *   sempre que uma transacao da posicao muda.</li>
 *   <li><b>MANUAL</b> — lancado pelo usuario, para provento que nenhuma fonte
 *   tem. O recalculador nao mexe nele, e ele nunca vai para o catalogo.</li>
 * </ul>
 */
public enum DividendOrigin {
    AUTO,
    MANUAL
}

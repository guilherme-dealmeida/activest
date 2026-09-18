package com.primevest.domain.portfolio;

import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import com.primevest.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * As regras de saldo que valem para qualquer tipo de ativo.
 *
 * <ul>
 *   <li><b>A</b> — nao se vende o que nao esta na carteira;</li>
 *   <li><b>B</b> — nao se vende mais do que se tem, <b>em nenhuma data</b>;</li>
 *   <li><b>I</b> — posicao em aberto nao tem valor aplicado zerado ou negativo.</li>
 * </ul>
 *
 * <p>A regra B olha a linha do tempo inteira, e nao o saldo de hoje. Uma venda
 * lancada com data retroativa pode caber no saldo atual e ainda assim deixar
 * uma venda posterior a descoberto — e o saldo de uma carteira nunca esteve
 * negativo, nem por um dia. Por isso a validacao roda sobre a posicao ja
 * alterada: criar, editar e excluir passam pelo mesmo teste, e o que nao
 * fecha volta atras pela transacao de banco.</p>
 */
@Component
public class PositionGuard {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Regra B, nos tipos cujo saldo e por cota.
     *
     * @param chronological transacoes em ordem cronologica — de uma posicao
     *                      (renda variavel) ou de um ativo inteiro (Tesouro)
     * @param label         como o ativo aparece na mensagem de erro
     */
    public void validateShareLedger(List<Transaction> chronological, String label) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (Transaction tx : chronological) {
            BigDecimal qtd = tx.getShares();
            if (qtd == null) {
                continue;
            }
            saldo = tx.getType() == TransactionType.BUY ? saldo.add(qtd) : saldo.subtract(qtd);
            if (saldo.signum() < 0) {
                throw new BusinessException(
                    "Saldo insuficiente de " + label + " em " + tx.getTransactionDate().format(DATA)
                    + ": faltam " + numero(saldo.negate()) + " cotas nessa data."
                    + " Ajuste as compras do periodo antes de lancar esta venda.");
            }
        }
    }

    /**
     * Regra A. A venda exige posicao com compras; nao e ela que abre posicao.
     *
     * <p>Antes, vender um ativo que nao estava na carteira criava a posicao e
     * gravava a venda: o lancamento errado ficava no banco, invisivel, porque o
     * calculo da posicao simplesmente ignorava uma venda sem cotas.</p>
     */
    public void validateSellHasPosition(boolean temPosicaoComCompras, String label) {
        if (!temPosicaoComCompras) {
            throw new BusinessException(
                "Nao ha posicao de " + label + " na carteira para vender.");
        }
    }

    /**
     * Regra I. Uma posicao em aberto sempre tem valor aplicado.
     *
     * <p>Posicao liquidada fica de fora: terminar zerada e o estado esperado de
     * quem vendeu tudo.</p>
     */
    public void validateApplied(Position position, boolean settled, boolean hasTransactions, String label) {
        if (settled || !hasTransactions) {
            return;
        }
        if (position.invested().signum() <= 0) {
            throw new BusinessException(
                "A posicao de " + label + " ficaria sem valor aplicado."
                + " Uma posicao em aberto precisa de pelo menos uma compra.");
        }
    }

    /** Numero para mensagem: sem zeros a direita, em formato simples. */
    private String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}

package com.primevest.domain.portfolio;

import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import com.primevest.exception.BusinessException;
import org.springframework.stereotype.Component;

import com.primevest.domain.corporateaction.CorporateActionApplier;
import com.primevest.domain.corporateaction.StockEventStep;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
     * @return o saldo de cotas depois da ultima transacao
     */
    public BigDecimal validateShareLedger(List<Transaction> chronological, String label) {
        return validateShareLedger(chronological, List.of(), label);
    }

    /**
     * Regra B com os eventos societarios do papel (docs/planos/PLANO_BRAPI.md, F6).
     *
     * <p>Depois de um desdobramento 1:2, quem tinha 100 cotas vende 200 — a
     * nota de corretagem ja vem na quantidade nova. Sem o evento no meio da
     * linha do tempo, essa venda seria recusada por falta de saldo. O evento
     * vale a partir do dia seguinte a data-com, como no calculo da posicao, e
     * com data-com no futuro ainda nao vale.</p>
     *
     * @return o saldo de cotas de hoje, ja com os eventos
     */
    public BigDecimal validateShareLedger(List<Transaction> chronological, List<StockEventStep> stockEvents,
                                          String label) {
        List<StockEventStep> events = stockEvents == null ? List.of()
            : stockEvents.stream().sorted(java.util.Comparator.comparing(StockEventStep::dataCom)).toList();
        int next = 0;
        BigDecimal balance = BigDecimal.ZERO;
        for (Transaction tx : chronological) {
            BigDecimal qtd = tx.getShares();
            if (qtd == null) {
                continue;
            }
            while (next < events.size() && events.get(next).dataCom().isBefore(tx.getTransactionDate())) {
                balance = afterEvent(balance, events.get(next++));
            }
            balance = tx.getType() == TransactionType.BUY ? balance.add(qtd) : balance.subtract(qtd);
            if (balance.signum() < 0) {
                throw new BusinessException(
                    "Saldo insuficiente de " + label + " em " + tx.getTransactionDate().format(DATA)
                    + ": faltam " + number(balance.negate()) + " cotas nessa data."
                    + " Ajuste as compras do período antes de lançar esta venda.");
            }
        }
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        while (next < events.size() && events.get(next).dataCom().isBefore(today)) {
            balance = afterEvent(balance, events.get(next++));
        }
        return balance;
    }

    private static BigDecimal afterEvent(BigDecimal balance, StockEventStep event) {
        return CorporateActionApplier.apply(new CorporateActionApplier.Holding(balance, BigDecimal.ZERO), event).shares();
    }

    /**
     * Regra A. A venda exige posicao com compras; nao e ela que abre posicao.
     *
     * <p>Antes, vender um ativo que nao estava na carteira criava a posicao e
     * gravava a venda: o lancamento errado ficava no banco, invisivel, porque o
     * calculo da posicao simplesmente ignorava uma venda sem cotas.</p>
     */
    public void validateSellHasPosition(boolean hasPositionWithBuys, String label) {
        if (!hasPositionWithBuys) {
            throw new BusinessException(
                "Não há posição de " + label + " na carteira para vender.");
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
                "A posição de " + label + " ficaria sem valor aplicado."
                + " Uma posição em aberto precisa de pelo menos uma compra.");
        }
    }

    /** Numero para mensagem: sem zeros a direita, em formato simples. */
    private String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}

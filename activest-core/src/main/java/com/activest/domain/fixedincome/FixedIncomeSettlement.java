package com.activest.domain.fixedincome;

import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.transaction.Transaction;
import com.activest.domain.transaction.TransactionType;
import com.activest.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * A valorizacao e a liquidacao de uma posicao de Renda Fixa.
 *
 * <p>Renda Fixa nao tem cotacao: ninguem publica quanto vale hoje o CDB que
 * voce aplicou no banco. O que o usuario sabe e quanto resgatou — e e dai que
 * a valorizacao passa a ser corrigida. Vender por mais do que a posicao vale
 * significa que ela valia mais do que estava registrado.</p>
 *
 * <p>Tres numeros mandam aqui, e nenhum deles desconta o outro:</p>
 * <ul>
 *   <li><b>aplicado</b> — a soma das compras;</li>
 *   <li><b>resgatado</b> — a soma das vendas;</li>
 *   <li><b>valorizacao</b> — portfolio_assets.appreciation.</li>
 * </ul>
 *
 * <p>As regras que eles precisam respeitar, sempre, venha a mudanca de onde
 * vier — nova venda, edicao de compra, exclusao ou rotina futura de curva:</p>
 * <ul>
 *   <li><b>E</b> — aplicado + valorizacao nunca fica abaixo do resgatado;</li>
 *   <li><b>F</b> — os dois so se igualam quando a venda e liquidacao total;</li>
 *   <li><b>G</b> — valorizacao de renda fixa nunca e negativa.</li>
 * </ul>
 *
 * <p>E as duas formulas de venda:</p>
 * <ul>
 *   <li><b>liquidacao total</b>: valorizacao = resgatado - aplicado. O saldo
 *   fecha em zero e a posicao e liquidada;</li>
 *   <li><b>resgate parcial</b> que alcance ou passe o saldo: valorizacao =
 *   resgatado - aplicado + R$ 0,01. Sobra um centavo de proposito — a posicao
 *   continua em aberto, e e a regra F que exige que ela nao se iguale.</li>
 * </ul>
 *
 * <p>As duas miram um alvo em vez de somar um incremento, entao o calculo se
 * autocorrige: editar o valor de uma venda ja lancada chega ao mesmo resultado
 * que lanca-la assim da primeira vez, sem precisar guardar o quanto ela mexeu.
 * A excecao e a exclusao de uma venda, que deixa a valorizacao ganha para tras
 * — por decisao do projeto, ela fica para a rotina assincrona de rentabilidade
 * de renda fixa corrigir.</p>
 *
 * <p>Com uma ressalva: posicao sem nenhuma transacao zera a valorizacao. Sem
 * aplicacao nao ha o que valorizar, e a sobra ficaria contando como patrimonio
 * — excluir todas as transacoes de um CDB liquidado com ganho de R$ 300 deixava
 * R$ 300 de "valor atual" numa carteira vazia.</p>
 */
@Component
public class FixedIncomeSettlement {

    /**
     * O que sobra num resgate parcial que alcanca o saldo. A regra F proibe
     * igualar sem liquidar, entao a posicao fica com a menor sobra que o campo
     * de dinheiro representa.
     */
    private static final BigDecimal RESIDUE = new BigDecimal("0.01");

    private static final int MONEY_SCALE = 2;
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    /**
     * Regrava valorizacao e liquidacao da posicao e valida E, F e G.
     *
     * <p>Roda depois de QUALQUER escrita na posicao. A regra J esta embutida:
     * a liquidacao nao e mantida de uma vez para a outra, e sim derivada de
     * novo a cada passagem — se a venda que liquidou deixou de existir ou
     * deixou de ser total, a posicao volta a ficar aberta.</p>
     *
     * <p>As duas formulas so valem quando a escrita foi numa VENDA. E o resgate
     * que autoriza corrigir a valorizacao para cima — ele e a prova de quanto a
     * posicao valia. Uma compra editada ou excluida nao prova nada: se ela
     * deixar o resgatado acima do saldo, a resposta e a regra E recusando a
     * alteracao, e nao uma valorizacao inventada para cobrir a diferenca. Sem
     * essa distincao, apagar uma compra de R$ 50 de uma posicao ja resgatada
     * criaria R$ 42 de valorizacao do nada.</p>
     *
     * @param pa            a posicao de Renda Fixa
     * @param chronological transacoes da posicao em ordem cronologica
     * @param vendaLancada  a escrita que disparou este recalculo foi numa venda
     */
    public void recompute(PortfolioAsset pa, List<Transaction> chronological, boolean recordedSale) {
        String label = label(pa);

        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal redeemed = BigDecimal.ZERO;
        boolean fullSettlement = false;

        for (Transaction tx : chronological) {
            BigDecimal value = tx.getTotalAmount() != null ? tx.getTotalAmount() : BigDecimal.ZERO;
            if (tx.getType() == TransactionType.BUY) {
                invested = invested.add(value);
            } else {
                redeemed = redeemed.add(value);
                fullSettlement = fullSettlement || tx.isFullSettlement();
            }
        }

        invested = scale(invested);
        redeemed = scale(redeemed);
        BigDecimal appreciation = scale(pa.getAppreciation() != null ? pa.getAppreciation() : BigDecimal.ZERO);

        if (fullSettlement) {
            // 3.1.1 — o resgate passa a definir quanto a posicao valia.
            appreciation = redeemed.subtract(invested);
            if (appreciation.signum() < 0) {
                throw new BusinessException(
                    "Liquidação total de " + label + ": o resgate de " + money(redeemed)
                    + " é menor que o valor aplicado (" + money(invested) + ")."
                    + " Em renda fixa não existe valorização negativa.");
            }
        } else if (recordedSale && redeemed.signum() > 0
                && redeemed.compareTo(invested.add(appreciation)) >= 0) {
            // 3.1.2 — resgate parcial que alcanca o saldo: a posicao valia mais
            // do que estava registrado, e sobra o centavo que a regra F exige.
            appreciation = redeemed.subtract(invested).add(RESIDUE);
        } else if (chronological.isEmpty()) {
            // Sem transacao nenhuma a posicao nao tem aplicado: a valorizacao
            // que restou de uma venda excluida nao pertence a mais nada.
            appreciation = BigDecimal.ZERO.setScale(MONEY_SCALE);
        }

        // Regra G.
        if (appreciation.signum() < 0) {
            throw new BusinessException(
                "A valorização de " + label + " ficaria negativa (" + money(appreciation) + ")."
                + " Em renda fixa isso não existe.");
        }

        BigDecimal grossBalance = invested.add(appreciation);

        // Regra E.
        if (grossBalance.compareTo(redeemed) < 0) {
            throw new BusinessException(
                "Em " + label + ", o total resgatado (" + money(redeemed) + ") passaria do valor aplicado"
                + " mais a valorização (" + money(grossBalance) + ")."
                + " Ajuste as compras ou a valorização antes desta alteração.");
        }

        // Regra F.
        if (grossBalance.compareTo(redeemed) == 0 && !fullSettlement && redeemed.signum() > 0) {
            throw new BusinessException(
                "Em " + label + ", o total resgatado (" + money(redeemed) + ") igualaria o valor aplicado"
                + " mais a valorização. Zerar a posição só é possível marcando a venda"
                + " como liquidação total.");
        }

        pa.setAppreciation(appreciation);
        pa.setSettled(fullSettlement);
    }

    /**
     * Valorizacao pela curva contratada (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, 3.4): titulo em aberto passa a
     * valer o saldo na curva — valorizacao = saldo + resgatado − aplicado, nunca negativa (regra G). Titulo liquidado
     * fica com o ganho realizado no resgate; titulo sem curva (TR, dado faltando) fica com o que tinha.
     *
     * <p>Roda depois do {@link #recompute} (que valida as regras E, F e G da escrita) e tambem sozinha, nas rodadas de
     * recalculo. A curva ja trata o resgate acima do saldo (D4, o centavo da regra F) e a venda excluida (10.2): o
     * saldo e refeito do zero a cada passagem.</p>
     *
     * @return se a valorizacao mudou
     */
    public boolean applyCurve(PortfolioAsset pa, List<Transaction> chronological, FixedIncomeCurve.Result curve) {
        if (pa.isSettled() || chronological.isEmpty() || curve == null
                || curve.status() == FixedIncomeCurve.Status.UNSUPPORTED || curve.balance() == null) {
            return false;
        }
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal redeemed = BigDecimal.ZERO;
        for (Transaction tx : chronological) {
            BigDecimal value = tx.getTotalAmount() != null ? tx.getTotalAmount() : BigDecimal.ZERO;
            if (tx.getType() == TransactionType.BUY) {
                invested = invested.add(value);
            } else {
                redeemed = redeemed.add(value);
            }
        }
        BigDecimal appreciation = scale(curve.balance().add(redeemed).subtract(invested));
        if (appreciation.signum() < 0) {
            appreciation = BigDecimal.ZERO.setScale(MONEY_SCALE); // regra G
        }
        BigDecimal current = pa.getAppreciation() != null ? scale(pa.getAppreciation()) : null;
        if (current != null && current.compareTo(appreciation) == 0) {
            return false;
        }
        pa.setAppreciation(appreciation);
        return true;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal value) {
        return MONEY.format(value);
    }

    private String label(PortfolioAsset pa) {
        String ticker = pa.getAsset() != null ? pa.getAsset().getTicker() : "";
        FixedIncomeNote note = pa.getNote();
        String noteLabel = note != null ? note.getLabel() : "";
        return noteLabel != null && !noteLabel.isBlank() ? ticker + " (" + noteLabel + ")" : ticker;
    }
}

package com.primevest.domain.fixedincome;

import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import com.primevest.exception.BusinessException;
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
 */
@Component
public class FixedIncomeSettlement {

    /**
     * O que sobra num resgate parcial que alcanca o saldo. A regra F proibe
     * igualar sem liquidar, entao a posicao fica com a menor sobra que o campo
     * de dinheiro representa.
     */
    private static final BigDecimal RESIDUO = new BigDecimal("0.01");

    private static final int MONEY_SCALE = 2;
    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

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
    public void recompute(PortfolioAsset pa, List<Transaction> chronological, boolean vendaLancada) {
        String label = label(pa);

        BigDecimal aplicado = BigDecimal.ZERO;
        BigDecimal resgatado = BigDecimal.ZERO;
        boolean liquidacaoTotal = false;

        for (Transaction tx : chronological) {
            BigDecimal valor = tx.getTotalAmount() != null ? tx.getTotalAmount() : BigDecimal.ZERO;
            if (tx.getType() == TransactionType.BUY) {
                aplicado = aplicado.add(valor);
            } else {
                resgatado = resgatado.add(valor);
                liquidacaoTotal = liquidacaoTotal || tx.isFullSettlement();
            }
        }

        aplicado = escala(aplicado);
        resgatado = escala(resgatado);
        BigDecimal valorizacao = escala(pa.getAppreciation() != null ? pa.getAppreciation() : BigDecimal.ZERO);

        if (liquidacaoTotal) {
            // 3.1.1 — o resgate passa a definir quanto a posicao valia.
            valorizacao = resgatado.subtract(aplicado);
            if (valorizacao.signum() < 0) {
                throw new BusinessException(
                    "Liquidação total de " + label + ": o resgate de " + moeda(resgatado)
                    + " é menor que o valor aplicado (" + moeda(aplicado) + ")."
                    + " Em renda fixa não existe valorização negativa.");
            }
        } else if (vendaLancada && resgatado.signum() > 0
                && resgatado.compareTo(aplicado.add(valorizacao)) >= 0) {
            // 3.1.2 — resgate parcial que alcanca o saldo: a posicao valia mais
            // do que estava registrado, e sobra o centavo que a regra F exige.
            valorizacao = resgatado.subtract(aplicado).add(RESIDUO);
        }

        // Regra G.
        if (valorizacao.signum() < 0) {
            throw new BusinessException(
                "A valorização de " + label + " ficaria negativa (" + moeda(valorizacao) + ")."
                + " Em renda fixa isso não existe.");
        }

        BigDecimal saldoBruto = aplicado.add(valorizacao);

        // Regra E.
        if (saldoBruto.compareTo(resgatado) < 0) {
            throw new BusinessException(
                "Em " + label + ", o total resgatado (" + moeda(resgatado) + ") passaria do valor aplicado"
                + " mais a valorização (" + moeda(saldoBruto) + ")."
                + " Ajuste as compras ou a valorização antes desta alteração.");
        }

        // Regra F.
        if (saldoBruto.compareTo(resgatado) == 0 && !liquidacaoTotal && resgatado.signum() > 0) {
            throw new BusinessException(
                "Em " + label + ", o total resgatado (" + moeda(resgatado) + ") igualaria o valor aplicado"
                + " mais a valorização. Zerar a posição só é possível marcando a venda"
                + " como liquidação total.");
        }

        pa.setAppreciation(valorizacao);
        pa.setSettled(liquidacaoTotal);
    }

    private BigDecimal escala(BigDecimal valor) {
        return valor.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String moeda(BigDecimal valor) {
        return MOEDA.format(valor);
    }

    private String label(PortfolioAsset pa) {
        String ticker = pa.getAsset() != null ? pa.getAsset().getTicker() : "";
        FixedIncomeNote note = pa.getNote();
        String titulo = note != null ? note.getLabel() : "";
        return titulo != null && !titulo.isBlank() ? ticker + " (" + titulo + ")" : ticker;
    }
}

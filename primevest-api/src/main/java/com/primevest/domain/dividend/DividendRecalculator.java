package com.primevest.domain.dividend;

import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.transaction.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Regra unica de quanto uma posicao recebe de provento.
 *
 * <p>O total de um provento e valor por cota x cotas que a posicao tinha ao fim
 * da data-com. Essas cotas saem das transacoes, que sao a fonte da verdade da
 * posicao — e por isso um provento ja gravado fica errado sempre que uma
 * transacao anterior a data-com e lancada, editada ou apagada.</p>
 *
 * <p>Dois usos: o DividendSyncScheduler, ao criar ou conferir proventos vindos
 * da BrAPI, e o TransactionService, que recalcula os proventos da posicao a
 * cada mudanca de transacao. A regra mora aqui para os dois nunca divergirem.</p>
 *
 * <p>Os totais da posicao deixaram de ser gravados (V28), mas
 * dividends.total_amount continua: por isso este recalculo segue sendo chamado
 * a cada criacao, edicao ou exclusao de transacao.</p>
 */
@Component
@RequiredArgsConstructor
public class DividendRecalculator {

    private final PositionCalculator positionCalculator;
    private final DividendRepository dividendRepository;

    /** Transacoes da posicao em ordem cronologica (data, depois criacao). */
    public List<Transaction> chronologicalTransactions(PortfolioAsset pa) {
        return positionCalculator.chronologicalTransactions(pa);
    }

    /**
     * Cotas ao fim da data-com, a partir de transacoes em ordem cronologica.
     *
     * <p>A contagem e a da posicao ({@link PositionCalculator#sharesOn}): compra
     * soma, venda subtrai e o saldo nunca fica negativo. Compra feita na propria
     * data-com conta; a partir do dia seguinte (data ex) nao. Transacoes sem
     * quantidade (Renda Fixa) nao somam nada.</p>
     */
    public BigDecimal sharesOn(List<Transaction> chronological, LocalDate recordDate) {
        return positionCalculator.sharesOn(chronological, recordDate);
    }

    public BigDecimal totalAmount(BigDecimal amountPerShare, BigDecimal shares) {
        return amountPerShare.multiply(shares).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Recalcula os proventos ja gravados da posicao.
     *
     * <p>Provento cuja data-com ficou sem cotas e apagado: a transacao que dava
     * direito a ele deixou de existir ou mudou de data. Os que ainda tem cotas
     * recebem o total novo. Proventos que passaram a ter direito e ainda nao
     * existem nao nascem aqui — dependem da BrAPI e entram na proxima
     * sincronizacao.</p>
     *
     * @param chronological transacoes da posicao em ordem cronologica
     */
    public void recalculate(PortfolioAsset pa, List<Transaction> chronological) {
        for (Dividend d : dividendRepository.findByPortfolioAssetIdOrderByExDateDesc(pa.getId())) {
            BigDecimal shares = sharesOn(chronological, d.getExDate());
            if (shares.compareTo(BigDecimal.ZERO) <= 0) {
                dividendRepository.delete(d);
                continue;
            }
            BigDecimal total = totalAmount(d.getAmountPerShare(), shares);
            if (d.getTotalAmount() == null || d.getTotalAmount().compareTo(total) != 0) {
                d.setTotalAmount(total);
                dividendRepository.save(d);
            }
        }
    }
}

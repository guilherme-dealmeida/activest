package com.primevest.domain.dividend;

import com.primevest.domain.corporateaction.StockEventStep;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PositionCalculator;
import com.primevest.domain.transaction.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regra unica de quanto uma posicao recebe de provento.
 *
 * <p>O total de um provento e valor por cota x cotas que a posicao tinha ao fim
 * da data-com. Essas cotas saem das transacoes, que sao a fonte da verdade da
 * posicao, ajustadas pelos desdobramentos, grupamentos e bonificacoes do papel
 * — e por isso um provento ja gravado fica errado sempre que uma transacao
 * anterior a data-com e lancada, editada ou apagada.</p>
 *
 * <p>Duas camadas (V20): o evento do papel mora no catalogo
 * ({@link AssetCashEvent}, sem usuario) e cada posicao tem a sua conta em
 * {@link Dividend}. Este recalculo e o unico caminho que cria, corrige e apaga
 * os proventos AUTO:</p>
 * <ul>
 *   <li>evento com cotas na data-com → provento criado ou com o total corrigido;</li>
 *   <li>evento sem cotas na data-com → provento apagado (a transacao que dava
 *   direito saiu ou mudou de data);</li>
 *   <li>evento de rotulo desconhecido (tipo nulo) → nada, ate ser mapeado.</li>
 * </ul>
 * <p>Os MANUAL nao sao tocados.</p>
 *
 * <p>Chamado pelo TransactionService a cada criacao, edicao ou exclusao de
 * transacao, e pela rotina de proventos depois de gravar eventos novos. Como os
 * eventos ja estao no catalogo, uma compra retroativa passa a receber os
 * proventos antigos na hora, sem nova chamada a B3.</p>
 */
@Component
@RequiredArgsConstructor
public class DividendRecalculator {

    private static final int PER_SHARE_SCALE = 8;

    private final PositionCalculator positionCalculator;
    private final DividendRepository dividendRepository;
    private final AssetCashEventRepository cashEventRepository;

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
     * Refaz os proventos AUTO da posicao a partir dos eventos do catalogo.
     *
     * @param chronological transacoes da posicao em ordem cronologica
     * @return quantos proventos foram criados
     */
    public int recalculate(PortfolioAsset pa, List<Transaction> chronological) {
        if (pa.getAsset() == null || pa.getAsset().getId() == null) {
            return 0;
        }
        List<AssetCashEvent> events = cashEventRepository.findByAssetIdOrderByDataComAsc(pa.getAsset().getId());
        List<Dividend> autos = pa.getId() != null ? dividendRepository.findAutoByPortfolioAssetId(pa.getId()) : List.of();
        if (events.isEmpty() && autos.isEmpty()) {
            return 0;
        }

        List<StockEventStep> stockEvents = positionCalculator.stockEventsOf(pa.getAsset());
        Map<Long, Dividend> byEvent = new HashMap<>();
        for (Dividend d : autos) {
            if (d.getCashEvent() != null) {
                byEvent.put(d.getCashEvent().getId(), d);
            }
        }

        int created = 0;
        Set<Long> kept = new HashSet<>();
        for (AssetCashEvent event : events) {
            if (event.getType() == null) {
                continue;
            }
            BigDecimal shares = positionCalculator.sharesOn(chronological, stockEvents, event.getDataCom());
            Dividend existing = byEvent.get(event.getId());
            if (shares.signum() <= 0) {
                continue;
            }
            kept.add(event.getId());

            BigDecimal perShare = event.getValuePerShare().setScale(PER_SHARE_SCALE, RoundingMode.HALF_UP);
            BigDecimal total = totalAmount(event.getValuePerShare(), shares);
            if (existing == null) {
                dividendRepository.save(Dividend.builder()
                        .portfolioAsset(pa)
                        .cashEvent(event)
                        .origin(DividendOrigin.AUTO)
                        .type(event.getType())
                        .amountPerShare(perShare)
                        .exDate(event.getDataCom())
                        .paymentDate(event.getPaymentDate())
                        .sharesOnDate(shares)
                        .totalAmount(total)
                        .build());
                created++;
            } else if (changed(existing, event, perShare, shares, total)) {
                existing.setType(event.getType());
                existing.setAmountPerShare(perShare);
                existing.setExDate(event.getDataCom());
                existing.setPaymentDate(event.getPaymentDate());
                existing.setSharesOnDate(shares);
                existing.setTotalAmount(total);
                dividendRepository.save(existing);
            }
        }

        // AUTO sem direito (ou cujo evento deixou de gerar provento) sai.
        for (Dividend d : autos) {
            if (d.getCashEvent() == null || !kept.contains(d.getCashEvent().getId())) {
                dividendRepository.delete(d);
            }
        }
        return created;
    }

    private static boolean changed(Dividend d, AssetCashEvent e, BigDecimal perShare, BigDecimal shares, BigDecimal total) {
        return d.getType() != e.getType()
                || !java.util.Objects.equals(d.getPaymentDate(), e.getPaymentDate())
                || !d.getExDate().equals(e.getDataCom())
                || d.getAmountPerShare().compareTo(perShare) != 0
                || d.getSharesOnDate() == null || d.getSharesOnDate().compareTo(shares) != 0
                || d.getTotalAmount() == null || d.getTotalAmount().compareTo(total) != 0;
    }
}

package com.activest.domain.dividend;

import com.activest.domain.corporateaction.StockEventStep;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.PositionCalculator;
import com.activest.domain.transaction.Transaction;
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
 * <p>Cada provento AUTO sai daqui com o IR retido calculado
 * ({@link WithholdingTaxCalculator}, regras em {@link WithholdingTaxRule}):
 * bruto, aliquota, IR e liquido nascem juntos, em todo caminho — rotina diaria,
 * rotina sob demanda, transacao lancada/editada/apagada (docs/planos/PLANO_IR_PROVENTOS.md,
 * 4.5). Este e o unico lugar que grava ou apaga provento.</p>
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
    private final WithholdingTaxRuleRepository withholdingTaxRuleRepository;

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

    /** Total bruto como a B3: truncado nos centavos (0,05968401166 x 125 = 7,46; 10,1972 -> 10,19). */
    public BigDecimal totalAmount(BigDecimal amountPerShare, BigDecimal shares) {
        return WithholdingTaxCalculator.gross(amountPerShare, shares);
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
        // Poucas linhas e raramente mudam: lidas uma vez por posicao.
        List<WithholdingTaxRule> taxRules = withholdingTaxRuleRepository.findAll();
        Long assetTypeId = pa.getAsset().getAssetType() != null ? pa.getAsset().getAssetType().getId() : null;
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
            WithholdingTaxCalculator.Result tax = WithholdingTaxCalculator.calculate(taxRules,
                    new WithholdingTaxCalculator.Input(event.getType(), assetTypeId, event.getApprovedOn(),
                            event.getDataCom(), event.getPaymentDate(), event.getValuePerShare(), shares));
            if (existing == null) {
                Dividend d = Dividend.builder()
                        .portfolioAsset(pa)
                        .cashEvent(event)
                        .origin(DividendOrigin.AUTO)
                        .type(event.getType())
                        .amountPerShare(perShare)
                        .exDate(event.getDataCom())
                        .paymentDate(event.getPaymentDate())
                        .sharesOnDate(shares)
                        .build();
                d.applyTax(tax);
                dividendRepository.save(d);
                created++;
            } else if (changed(existing, event, perShare, shares, tax)) {
                existing.setType(event.getType());
                existing.setAmountPerShare(perShare);
                existing.setExDate(event.getDataCom());
                existing.setPaymentDate(event.getPaymentDate());
                existing.setSharesOnDate(shares);
                existing.applyTax(tax);
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

    static boolean changed(Dividend d, AssetCashEvent e, BigDecimal perShare, BigDecimal shares,
                           WithholdingTaxCalculator.Result tax) {
        return d.getTaxCheckedAt() == null
                || d.getType() != e.getType()
                || !java.util.Objects.equals(d.getPaymentDate(), e.getPaymentDate())
                || !d.getExDate().equals(e.getDataCom())
                || d.getAmountPerShare().compareTo(perShare) != 0
                || d.getSharesOnDate() == null || d.getSharesOnDate().compareTo(shares) != 0
                || differs(d.getTotalAmount(), tax.gross())
                || !java.util.Objects.equals(ruleId(d.getWithholdingRule()), ruleId(tax.rule()))
                || differs(d.getWithholdingRate(), tax.rate())
                || differs(d.getWithholdingTax(), tax.tax())
                || differs(d.getNetAmountPerShare(), tax.netPerShare())
                || differs(d.getNetAmount(), tax.net());
    }

    private static Long ruleId(WithholdingTaxRule r) {
        return r != null ? r.getId() : null;
    }

    /** Diferentes, tratando nulo como valor (sem regra) e ignorando a escala. */
    private static boolean differs(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.compareTo(b) != 0;
    }
}

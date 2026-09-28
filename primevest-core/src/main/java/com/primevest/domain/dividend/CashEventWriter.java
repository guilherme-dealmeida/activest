package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Grava um evento de provento no catalogo ({@link AssetCashEvent}), ou completa o que ja
 * existe — a mesma noticia vinda de outra fonte (V22, V24). Usado pela rotina de proventos da
 * adm-api (B3, historico, FNET) e pelo extrato da B3 na app-api: as duas gravam com a mesma
 * unicidade e a mesma precedencia (a fonte oficial manda na data de pagamento que veio do
 * extrato ou foi deduzida). Estava no {@code ProventosSyncService} (PLANO_ADM_API.md, 10.2).
 */
@Slf4j
@Component
public class CashEventWriter {

    /** Casas do valor por cota/acao gravado. */
    public static final int VALUE_SCALE = 11;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Um evento como veio da fonte. {@code seq}: parcela (linhas iguais na mesma resposta). */
    public record CashEventData(DividendType type, String label, LocalDate dataCom, LocalDate paymentDate,
                                BigDecimal value, LocalDate approvedOn, String relatedTo, String isin,
                                Boolean incomeTaxExempt, CashEventSource source, String sourceRef, short seq) {
    }

    private final AssetCashEventRepository cashEventRepository;
    private final AssetRepository assetRepository;
    private final TransactionTemplate tx;

    public CashEventWriter(AssetCashEventRepository cashEventRepository, AssetRepository assetRepository,
                           PlatformTransactionManager transactionManager) {
        this.cashEventRepository = cashEventRepository;
        this.assetRepository = assetRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Grava o evento, ou completa o que ja existe. Devolve se o evento e novo. */
    public boolean save(Asset asset, CashEventData d) {
        BigDecimal value = d.value().setScale(VALUE_SCALE, RoundingMode.HALF_UP);
        String label = d.label() == null ? "?" : d.label().trim();
        Boolean created = tx.execute(s -> {
            AssetCashEvent existing = cashEventRepository.findSame(asset.getId(), d.type(), d.dataCom(), value, d.seq())
                    .orElse(null);
            if (existing != null) {
                boolean dirty = false;
                if (existing.getPaymentDate() == null && d.paymentDate() != null) {
                    existing.setPaymentDate(d.paymentDate());
                    existing.setPaymentDateSource(d.source() == CashEventSource.EXTRATO_B3
                            ? PaymentDateSource.EXTRATO_B3 : null);
                    dirty = true;
                } else if (existing.getPaymentDateSource() != null && d.paymentDate() != null
                        && d.source() != CashEventSource.EXTRATO_B3) {
                    // A data veio do extrato ou foi deduzida; a fonte oficial
                    // manda (V24).
                    existing.setPaymentDate(d.paymentDate());
                    existing.setPaymentDateSource(null);
                    dirty = true;
                }
                if (existing.getApprovedOn() == null && d.approvedOn() != null) {
                    existing.setApprovedOn(d.approvedOn());
                    dirty = true;
                }
                if (existing.getRelatedTo() == null && d.relatedTo() != null) {
                    existing.setRelatedTo(d.relatedTo());
                    dirty = true;
                }
                if (existing.getIncomeTaxExempt() == null && d.incomeTaxExempt() != null) {
                    existing.setIncomeTaxExempt(d.incomeTaxExempt());
                    dirty = true;
                }
                if (dirty) {
                    cashEventRepository.save(existing);
                }
                return false;
            }
            cashEventRepository.save(AssetCashEvent.builder()
                    .asset(assetRepository.getReferenceById(asset.getId()))
                    .type(d.type())
                    .label(label.length() > 60 ? label.substring(0, 60) : label)
                    .dataCom(d.dataCom())
                    .paymentDate(d.paymentDate())
                    .paymentDateSource(d.source() == CashEventSource.EXTRATO_B3 && d.paymentDate() != null
                            ? PaymentDateSource.EXTRATO_B3 : null)
                    .valuePerShare(value)
                    .seq(d.seq())
                    .approvedOn(d.approvedOn())
                    .relatedTo(d.relatedTo() != null && d.relatedTo().length() > 100 ? d.relatedTo().substring(0, 100) : d.relatedTo())
                    .isin(d.isin())
                    .incomeTaxExempt(d.incomeTaxExempt())
                    .source(d.source())
                    .sourceRef(d.sourceRef())
                    .build());
            return true;
        });
        if (Boolean.TRUE.equals(created) && d.type() == null) {
            log.warn("Proventos: rotulo desconhecido '{}' em {} (data-com {}) — gravado, mas nao gera provento ate ser mapeado em DividendType.",
                    label, asset.getTicker(), d.dataCom().format(BR));
        }
        return Boolean.TRUE.equals(created);
    }
}

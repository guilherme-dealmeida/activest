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
 * existe — a mesma noticia vinda de outra fonte (V22). So a rotina de proventos da adm-api
 * (B3, historico, FNET) grava aqui: evento do catalogo vem de integracao, nunca de importacao
 * (PLANO_CARGA_INICIAL.md, regra C — o extrato da B3 nao grava mais no catalogo).
 *
 * <p>Precedencia na mesma noticia: a data de pagamento que veio de outra fonte
 * ({@link PaymentDateSource}, ex.: relatorio de proventos da CVM) cede a data da propria fonte do
 * evento; e, para FII, o <b>FNET substitui a B3</b> (D15): o evento gravado pela B3 como reserva
 * passa a ser do FNET quando o aviso chega.</p>
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
                if (existing.getSource() == CashEventSource.B3 && d.source() == CashEventSource.FNET) {
                    // D15: FNET e a fonte principal de FII; a B3 era reserva
                    existing.setSource(CashEventSource.FNET);
                    existing.setSourceRef(d.sourceRef());
                    if (d.paymentDate() != null) {
                        existing.setPaymentDate(d.paymentDate());
                        existing.setPaymentDateSource(null);
                    }
                    if (d.incomeTaxExempt() != null) {
                        existing.setIncomeTaxExempt(d.incomeTaxExempt());
                    }
                    dirty = true;
                } else if (existing.getSource() == CashEventSource.FNET && d.source() == CashEventSource.FNET
                        && d.sourceRef() != null && !d.sourceRef().equals(existing.getSourceRef())
                        && (existing.getSourceRef() == null || !existing.getSourceRef().matches("\\d+"))) {
                    // evento do FNET sem o id do aviso (reclassificado pela V38, que deixou a referencia
                    // da linha do extrato): guarda o aviso
                    existing.setSourceRef(d.sourceRef());
                    dirty = true;
                }
                if (existing.getPaymentDate() == null && d.paymentDate() != null) {
                    existing.setPaymentDate(d.paymentDate());
                    existing.setPaymentDateSource(null);
                    dirty = true;
                } else if (existing.getPaymentDateSource() != null && d.paymentDate() != null) {
                    // a data veio de outra fonte (CVM); a da fonte do evento manda
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

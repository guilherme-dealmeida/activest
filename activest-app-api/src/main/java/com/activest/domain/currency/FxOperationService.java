package com.activest.domain.currency;

import com.activest.domain.currency.dto.FxOperationDTOs;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Operações de câmbio do usuário (docs/planos/PLANO_CADASTRO_MOEDAS.md, 6.3, C7, D19): remessa de reais para a
 * corretora no exterior (finalidade investimento ou conta própria) e retorno. O IOF sai da regra vigente na data pela
 * finalidade; o câmbio efetivo = recebido ÷ (enviado − IOF − tarifa) na remessa, (recebido + IOF + tarifa) ÷ enviado no
 * retorno — o "custo do dólar" de cada operação. Insumo do saldo em moeda estrangeira, consumido pelos escopos dos
 * ativos internacionais (sem tela neste escopo).
 */
@Service
@RequiredArgsConstructor
public class FxOperationService {

    private final FxOperationRepository operations;
    private final CurrencyRepository currencies;
    private final ForeignTaxRuleService taxRules;
    private final ExchangeRateService exchangeRates;

    @Transactional(readOnly = true)
    public List<FxOperationDTOs.Response> list(UUID userId) {
        return operations.findByUserIdOrderByOperationDateDescCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public FxOperationDTOs.Response create(UUID userId, FxOperationDTOs.Request r) {
        FxOperation op = FxOperation.builder().userId(userId).build();
        apply(op, r);
        return toResponse(operations.save(op));
    }

    @Transactional
    public FxOperationDTOs.Response update(UUID userId, UUID id, FxOperationDTOs.Request r) {
        FxOperation op = owned(userId, id);
        apply(op, r);
        return toResponse(operations.save(op));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        operations.delete(owned(userId, id));
    }

    private FxOperation owned(UUID userId, UUID id) {
        return operations.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Operação de câmbio não encontrada"));
    }

    /** C7: valida as moedas, acha a regra de IOF da finalidade na data e calcula IOF e câmbio efetivo. */
    void apply(FxOperation op, FxOperationDTOs.Request r) {
        String from = r.fromCurrencyCode().toUpperCase();
        String to = r.toCurrencyCode().toUpperCase();
        for (String code : List.of(from, to)) {
            Currency c = currencies.findById(code)
                .orElseThrow(() -> new BusinessException("Moeda não cadastrada: " + code));
            if (!c.isActive()) {
                throw new BusinessException("Moeda inativa: " + code);
            }
        }
        if (from.equals(to)) {
            throw new BusinessException("A operação de câmbio precisa de duas moedas diferentes.");
        }
        boolean outbound = exchangeRates.isBase(from);
        if (!outbound && !exchangeRates.isBase(to)) {
            throw new BusinessException("Uma das moedas da operação precisa ser o real.");
        }
        if ((r.purpose() == FxPurpose.INFLOW) == outbound) {
            throw new BusinessException(outbound
                ? "Saída de reais é remessa: finalidade investimento ou conta própria."
                : "Entrada de reais é retorno: finalidade ingresso.");
        }
        BigDecimal fee = r.feeAmount() == null ? BigDecimal.ZERO : r.feeAmount();
        // IOF sobre os reais da operação: o enviado na remessa, o recebido no retorno
        BigDecimal baseAmount = outbound ? r.amountSent() : r.amountReceived();
        ForeignTaxRule rule = taxRules.find(ForeignTax.IOF_FX, r.purpose().iofEvent(), null, null, r.operationDate())
            .orElse(null);
        BigDecimal iof = ForeignTaxCalculator.flat(baseAmount, rule);
        BigDecimal reaisNet = outbound ? r.amountSent().subtract(iof).subtract(fee) : r.amountReceived().add(iof).add(fee);
        if (outbound && reaisNet.signum() <= 0) {
            throw new BusinessException("IOF e tarifa não podem consumir todo o valor enviado.");
        }
        BigDecimal effective = outbound
            ? reaisNet.divide(r.amountReceived(), 8, RoundingMode.HALF_EVEN)
            : reaisNet.divide(r.amountSent(), 8, RoundingMode.HALF_EVEN);
        op.setOperationDate(r.operationDate());
        op.setFromCurrencyCode(from);
        op.setToCurrencyCode(to);
        op.setAmountSent(r.amountSent());
        op.setAmountReceived(r.amountReceived());
        op.setPurpose(r.purpose());
        op.setForeignTaxRule(rule);
        op.setIofRate(rule != null ? rule.getRate() : BigDecimal.ZERO);
        op.setIofAmount(iof);
        op.setFeeAmount(fee);
        op.setEffectiveRate(effective);
        op.setInstitution(r.institution());
        op.setNotes(r.notes());
    }

    private FxOperationDTOs.Response toResponse(FxOperation op) {
        ForeignTaxRule rule = op.getForeignTaxRule();
        return new FxOperationDTOs.Response(op.getId(), op.getOperationDate(), op.getFromCurrencyCode(),
            op.getToCurrencyCode(), op.getAmountSent(), op.getAmountReceived(), op.getEffectiveRate(), op.getPurpose(),
            op.getIofRate(), op.getIofAmount(), op.getFeeAmount(), op.getInstitution(),
            rule != null ? rule.getLegalBasis() : null, op.getNotes());
    }
}

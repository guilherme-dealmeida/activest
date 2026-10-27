package com.activest.domain.currency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Escolhe a regra de tributo do exterior vigente (V9). Entre as regras do tributo e do evento vigentes na data, a mais
 * específica vence: país informado antes de "qualquer país", tipo de ativo informado antes de "qualquer tipo".
 */
@Service
public class ForeignTaxRuleService {

    private final ForeignTaxRuleRepository rules;

    public ForeignTaxRuleService(ForeignTaxRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<ForeignTaxRule> all() {
        return rules.findAllOrdered();
    }

    @Transactional(readOnly = true)
    public Optional<ForeignTaxRule> find(ForeignTax tax, ForeignTaxEvent event, String countryCode, Long assetTypeId,
                                         LocalDate date) {
        return select(rules.findAllOrdered(), tax, event, countryCode, assetTypeId, date);
    }

    /** Escolha pura (testável sem banco). */
    public static Optional<ForeignTaxRule> select(List<ForeignTaxRule> candidates, ForeignTax tax, ForeignTaxEvent event,
                                                  String countryCode, Long assetTypeId, LocalDate date) {
        return candidates.stream()
            .filter(r -> r.getTax() == tax && r.getEvent() == event && r.validOn(date))
            .filter(r -> r.getCountryCode() == null || r.getCountryCode().equals(countryCode))
            .filter(r -> r.getAssetType() == null || r.getAssetType().getId().equals(assetTypeId))
            .max(Comparator.comparingInt(ForeignTaxRuleService::specificity));
    }

    private static int specificity(ForeignTaxRule r) {
        return (r.getCountryCode() != null ? 2 : 0) + (r.getAssetType() != null ? 1 : 0);
    }
}

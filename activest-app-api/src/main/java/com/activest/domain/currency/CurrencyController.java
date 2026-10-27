package com.activest.domain.currency;

import com.activest.config.SecurityUtils;
import com.activest.domain.currency.dto.FxOperationDTOs;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Moedas e câmbio para as telas (docs/planos/PLANO_CADASTRO_MOEDAS.md, 10.4). Leitura: o cadastro se edita na tela de
 * administrador (adm-api). Inclui as regras de tributo do exterior (consulta, D18) e as operações de câmbio do
 * usuário (D19, sem tela neste escopo).
 */
@RestController
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyRepository currencies;
    private final ExchangeRateService exchangeRates;
    private final ExchangeRateRepository rates;
    private final ForeignTaxRuleService taxRules;
    private final FxOperationService fxOperations;

    public record CurrencyDTO(String code, String name, String symbol, int decimals, boolean base) {
    }

    /** Câmbio de uma data: o valor e o boletim usado (diferente da data pedida = carregado, D27). */
    public record RateDTO(String currencyCode, LocalDate date, BigDecimal buy, BigDecimal sell, LocalDate rateDate,
                          boolean carried) {
    }

    public record RatePointDTO(LocalDate date, BigDecimal buy, BigDecimal sell) {
    }

    public record TaxRuleDTO(Long id, ForeignTax tax, ForeignTaxEvent event, String countryCode, String assetType,
                             BigDecimal rate, String brackets, LocalDate validFrom, LocalDate validTo,
                             String legalBasis) {
    }

    @GetMapping("/api/v1/currencies")
    public List<CurrencyDTO> list() {
        return currencies.findAllByOrderByBaseDescCodeAsc().stream().filter(Currency::isActive)
            .map(c -> new CurrencyDTO(c.getCode(), c.getName(), c.getSymbol(), c.getDecimals(), c.isBase())).toList();
    }

    /** Câmbio padrão de uma data (o modal de transação pré-preenche com a venda — D5). 404 sem nenhum boletim. */
    @GetMapping("/api/v1/currencies/{code}/rate")
    public ResponseEntity<RateDTO> rate(@PathVariable String code,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        String c = code.toUpperCase();
        var sell = exchangeRates.rateOn(c, date, RateSide.SELL);
        var buy = exchangeRates.rateOn(c, date, RateSide.BUY);
        if (sell.isEmpty() || buy.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new RateDTO(c, date, buy.get().rate(), sell.get().rate(), sell.get().rateDate(),
            sell.get().carried()));
    }

    /** Série do câmbio (gráfico da tela Moedas). Sem período: o último ano. */
    @GetMapping("/api/v1/currencies/{code}/rates")
    public List<RatePointDTO> rates(@PathVariable String code,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusYears(1);
        return rates.findByCurrencyCodeAndReferenceDateBetweenOrderByReferenceDate(code.toUpperCase(), start, end)
            .stream().map(r -> new RatePointDTO(r.getReferenceDate(), r.getBuyRate(), r.getSellRate())).toList();
    }

    @GetMapping("/api/v1/foreign-tax-rules")
    public List<TaxRuleDTO> taxRules() {
        return taxRules.all().stream().map(r -> new TaxRuleDTO(r.getId(), r.getTax(), r.getEvent(), r.getCountryCode(),
            r.getAssetType() != null ? r.getAssetType().getDescription() : null, r.getRate(), r.getBrackets(),
            r.getValidFrom(), r.getValidTo(), r.getLegalBasis())).toList();
    }

    @GetMapping("/api/v1/fx-operations")
    public List<FxOperationDTOs.Response> fxOperations() {
        return fxOperations.list(SecurityUtils.getCurrentUserId());
    }

    @PostMapping("/api/v1/fx-operations")
    public FxOperationDTOs.Response createFxOperation(@Valid @RequestBody FxOperationDTOs.Request r) {
        return fxOperations.create(SecurityUtils.getCurrentUserId(), r);
    }

    @PutMapping("/api/v1/fx-operations/{id}")
    public FxOperationDTOs.Response updateFxOperation(@PathVariable UUID id, @Valid @RequestBody FxOperationDTOs.Request r) {
        return fxOperations.update(SecurityUtils.getCurrentUserId(), id, r);
    }

    @DeleteMapping("/api/v1/fx-operations/{id}")
    public ResponseEntity<Void> deleteFxOperation(@PathVariable UUID id) {
        fxOperations.delete(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}

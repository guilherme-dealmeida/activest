package com.activest.adm.domain.currency;

import com.activest.config.SecurityUtils;
import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.activest.domain.currency.Currency;
import com.activest.domain.currency.CurrencyRateSource;
import com.activest.domain.currency.CurrencyRepository;
import com.activest.domain.currency.FiatCurrencies;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tela de administrador "Moedas" (docs/planos/PLANO_CADASTRO_MOEDAS.md, D13 e 10.4): o cadastro de moedas é catálogo,
 * então mora na adm-api (como a tela de Tarefas; o proxy do front manda {@code /api/v1/admin/currencies} para cá). Só
 * {@code is_admin}. A moeda base não perde a marca, não ganha fonte de câmbio e não é desativada. Toda mudança atualiza
 * as moedas conhecidas das regras de importação e avisa a app-api (CAMBIO) para descartar o cache de câmbio.
 */
@RestController
@RequestMapping("/api/v1/admin/currencies")
@RequiredArgsConstructor
public class CurrencyAdminController {

    private final CurrencyRepository currencies;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final FiatCurrencies fiat;
    private final CatalogChangePublisher publisher;

    /** Uma moeda na lista: cadastro + cobertura da série de câmbio. */
    public record Row(String code, String name, String symbol, int decimals, boolean base, boolean active,
                      CurrencyRateSource rateSource, String countryCode, long rates, LocalDate firstDate,
                      LocalDate lastDate, BigDecimal lastBuy, BigDecimal lastSell) {
    }

    public record CreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "código ISO de 3 letras") String code,
        @NotBlank @Size(max = 60) String name,
        @NotBlank @Size(max = 6) String symbol,
        @NotNull @Min(0) @Max(8) Integer decimals,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "país ISO de 2 letras") String countryCode,
        @NotNull CurrencyRateSource rateSource,
        boolean active
    ) {
    }

    public record UpdateRequest(
        @NotBlank @Size(max = 60) String name,
        @NotBlank @Size(max = 6) String symbol,
        @NotNull @Min(0) @Max(8) Integer decimals,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "país ISO de 2 letras") String countryCode,
        @NotNull CurrencyRateSource rateSource,
        boolean active
    ) {
    }

    @GetMapping
    public List<Row> list() {
        requireAdmin();
        Map<String, Object[]> stats = new java.util.HashMap<>();
        jdbc.query("""
            SELECT r.currency_code, COUNT(*), MIN(r.reference_date), MAX(r.reference_date),
                   (SELECT x.buy_rate FROM exchange_rates x WHERE x.currency_code = r.currency_code
                     ORDER BY x.reference_date DESC LIMIT 1),
                   (SELECT x.sell_rate FROM exchange_rates x WHERE x.currency_code = r.currency_code
                     ORDER BY x.reference_date DESC LIMIT 1)
              FROM exchange_rates r GROUP BY r.currency_code""",
            rs -> { stats.put(rs.getString(1), new Object[]{rs.getLong(2), rs.getDate(3), rs.getDate(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6)}); });
        return currencies.findAllByOrderByBaseDescCodeAsc().stream().map(c -> {
            Object[] s = stats.get(c.getCode());
            return new Row(c.getCode(), c.getName(), c.getSymbol(), c.getDecimals(), c.isBase(), c.isActive(),
                c.getRateSource(), c.getCountryCode(), s == null ? 0 : (Long) s[0],
                s == null ? null : ((Date) s[1]).toLocalDate(), s == null ? null : ((Date) s[2]).toLocalDate(),
                s == null ? null : (BigDecimal) s[3], s == null ? null : (BigDecimal) s[4]);
        }).toList();
    }

    @PostMapping
    @Transactional
    public Row create(@Valid @RequestBody CreateRequest r) {
        requireAdmin();
        String code = r.code().toUpperCase();
        if (currencies.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Moeda já cadastrada: " + code);
        }
        if (r.rateSource() == CurrencyRateSource.NONE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Só a moeda base fica sem fonte de câmbio.");
        }
        currencies.save(Currency.builder().code(code).name(r.name().strip()).symbol(r.symbol().strip())
            .decimals(r.decimals().shortValue()).base(false).active(r.active()).rateSource(r.rateSource())
            .countryCode(r.countryCode() == null || r.countryCode().isBlank() ? null : r.countryCode().toUpperCase())
            .build());
        changed(code);
        return row(code);
    }

    @PutMapping("/{code}")
    @Transactional
    public Row update(@PathVariable String code, @Valid @RequestBody UpdateRequest r) {
        requireAdmin();
        Currency c = currencies.findById(code.toUpperCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Moeda não encontrada: " + code));
        if (c.isBase() && (!r.active() || r.rateSource() != CurrencyRateSource.NONE)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "A moeda base fica sempre ativa e sem fonte de câmbio.");
        }
        if (!c.isBase() && r.rateSource() == CurrencyRateSource.NONE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Só a moeda base fica sem fonte de câmbio.");
        }
        if (!r.active() && c.isActive()) {
            Long inUse = jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE currency_code = ? AND active",
                Long.class, c.getCode());
            if (inUse != null && inUse > 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Há " + inUse + " ativo(s) do catálogo cotado(s) em " + c.getCode() + ": a moeda não pode ser desativada.");
            }
        }
        c.setName(r.name().strip());
        c.setSymbol(r.symbol().strip());
        c.setDecimals(r.decimals().shortValue());
        c.setCountryCode(r.countryCode() == null || r.countryCode().isBlank() ? null : r.countryCode().toUpperCase());
        c.setRateSource(r.rateSource());
        c.setActive(r.active());
        currencies.save(c);
        changed(c.getCode());
        return row(c.getCode());
    }

    private void changed(String code) {
        currencies.flush();
        fiat.refresh();
        publisher.publish(CatalogChange.exchangeRates(Set.of(code)));
    }

    private Row row(String code) {
        return list().stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow();
    }

    private User requireAdmin() {
        User user = users.findById(SecurityUtils.getCurrentUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente administrador.");
        }
        return user;
    }
}

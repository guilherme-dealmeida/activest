package com.activest.domain.ceilingprice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.ceilingprice.CeilingPriceRules.Inputs;
import com.activest.domain.ceilingprice.CeilingPriceRules.Issue;
import com.activest.domain.ceilingprice.CeilingPriceRules.Level;
import com.activest.domain.ceilingprice.CeilingPriceRules.Limits;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.CeilingPriceInput;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.DefaultQuarter;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.DefaultValue;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.Defaults;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.Detail;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.InputOrigin;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.ListItem;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.QuarterInput;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.QuarterView;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.Result;
import com.activest.domain.ceilingprice.dto.CeilingPriceDTOs.Step;
import com.activest.domain.portfolio.PortfolioService;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
import com.activest.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Preço Teto pelo modelo PREÇO TETO V2 (docs/planos/PLANO_PRECO_TETO.md, 5 e 6). Grava só as entradas do usuário
 * (todas obrigatórias); o resultado sai delas pelo {@link CeilingPriceRules} a cada leitura. O catálogo entra só
 * pelos padrões ({@link CeilingPriceDefaults}) e pelos alertas que comparam a entrada com eles.
 */
@Service
@Transactional(readOnly = true)
public class CeilingPriceService {

    /** Mensagens do Preço Teto (alerta de preço e "padrão mudou"). Opcional nos testes que montam o serviço. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    CeilingPriceAlertService alerts;

    static final List<String> FIELDS = List.of("shares", "roePct", "payoutPct", "discountRatePct",
            "perpetualGrowthPct", "safetyMarginPct", "price");
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CeilingPriceRepository repository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final CeilingPriceDefaults defaults;
    private final PortfolioService portfolioService;
    private final ObjectMapper json;
    private final Limits limits;
    private final BigDecimal sharesDeviationPct;
    private final BigDecimal quarterDeviationPct;
    private final BigDecimal lowProfitPct;
    private final int stalePriceDays;
    private final int staleBalanceDays;

    public CeilingPriceService(CeilingPriceRepository repository, AssetRepository assetRepository,
                               UserRepository userRepository, CeilingPriceDefaults defaults,
                               PortfolioService portfolioService, ObjectMapper json,
                               @Value("${activest.ceiling-price.alerts.percent-min:0}") BigDecimal pctMin,
                               @Value("${activest.ceiling-price.alerts.percent-max:100}") BigDecimal pctMax,
                               @Value("${activest.ceiling-price.alerts.discount-min:5}") BigDecimal discountMin,
                               @Value("${activest.ceiling-price.alerts.discount-max:30}") BigDecimal discountMax,
                               @Value("${activest.ceiling-price.alerts.perpetual-growth-max:6}") BigDecimal growthMax,
                               @Value("${activest.ceiling-price.alerts.shares-deviation:20}") BigDecimal sharesDeviationPct,
                               @Value("${activest.ceiling-price.alerts.earnings-deviation:50}") BigDecimal quarterDeviationPct,
                               @Value("${activest.ceiling-price.alerts.low-earnings:3}") BigDecimal lowProfitPct,
                               @Value("${activest.ceiling-price.alerts.price-days:7}") int stalePriceDays,
                               @Value("${activest.ceiling-price.alerts.statement-days:150}") int staleBalanceDays) {
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.defaults = defaults;
        this.portfolioService = portfolioService;
        this.json = json;
        this.limits = new Limits(pctMin, pctMax, discountMin, discountMax, growthMax);
        this.sharesDeviationPct = sharesDeviationPct;
        this.quarterDeviationPct = quarterDeviationPct;
        this.lowProfitPct = lowProfitPct;
        this.stalePriceDays = stalePriceDays;
        this.staleBalanceDays = staleBalanceDays;
    }

    /** Recusa ao salvar, com os avisos que a causaram (422). */
    public static class Rejected extends RuntimeException {
        private final transient List<Issue> issues;

        public Rejected(String message, List<Issue> issues) {
            super(message);
            this.issues = issues;
        }

        public List<Issue> issues() {
            return issues;
        }
    }

    // ------------------------------------------------------------------ leitura

    public Defaults defaults(UUID userId, String ticker) {
        CeilingPriceDefaults.AssetInfo a = defaults.asset(ticker);
        UUID existing = repository.findByUserIdAndAssetId(userId, a.id()).map(CeilingPrice::getId).orElse(null);
        return defaults.of(a.ticker(), existing);
    }

    /** Calcula sem gravar: prévia do formulário. */
    public Result calculate(CeilingPriceInput in) {
        Defaults d = defaults.of(in.ticker(), null);
        Normalized n = normalize(in);
        return result(n, d);
    }

    public Detail detail(UUID userId, UUID id) {
        CeilingPrice cp = owned(userId, id);
        Defaults d = defaults.of(cp.getAsset().getTicker(), cp.getId());
        return toDetail(cp, d);
    }

    public List<ListItem> list(UUID userId) {
        List<CeilingPrice> all = repository.findByUserId(userId);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> averages = averagePrices(userId, all);
        List<ListItem> out = new ArrayList<>();
        for (CeilingPrice cp : all) {
            Defaults d;
            try {
                d = defaults.of(cp.getAsset().getTicker(), cp.getId());
            } catch (ResponseStatusException e) {
                d = null;
            }
            Normalized n = fromEntity(cp);
            CeilingPriceRules.Result r = CeilingPriceRules.calculate(n.inputs(), limits);
            BigDecimal today = d == null ? null : d.price().value();
            BigDecimal avg = averages.get(cp.getAsset().getId());
            BigDecimal reference = today != null ? today : cp.getPrice();
            int blocking = (int) r.issues().stream().filter(i -> i.level() == Level.BLOQUEIO).count();
            int warnings = (int) r.issues().stream().filter(i -> i.level() == Level.ATENCAO).count();
            out.add(new ListItem(cp.getId(), cp.getAsset().getTicker(), cp.getAsset().getName(),
                    cp.getAsset().getIssuer() != null ? cp.getAsset().getIssuer().getId() : null,
                    d == null ? null : d.hasLogo(),
                    perShare(r.ceilingPrice()), perShare(r.idealPrice()), cp.getSafetyMarginPct(),
                    cp.getPrice(), pct(r.ceilingMargin()), pct(r.idealMargin()),
                    today, d == null ? null : d.priceDate(),
                    pct(CeilingPriceRules.margin(r.ceilingPrice(), today)),
                    pct(CeilingPriceRules.margin(r.idealPrice(), today)),
                    avg, pct(CeilingPriceRules.margin(r.ceilingPrice(), avg)),
                    pct(CeilingPriceRules.margin(r.idealPrice(), avg)),
                    status(reference, r.ceilingPrice(), r.idealPrice()), blocking, warnings,
                    d != null && !changedDefaults(cp, d).isEmpty(),
                    cp.getQuarters().isEmpty() ? null : cp.getQuarters().get(cp.getQuarters().size() - 1).getPeriodEnd(),
                    cp.getCalculatedAt(), cp.isAlertEnabled()));
        }
        out.sort(Comparator.comparing(ListItem::ticker));
        return out;
    }

    /**
     * Situação do preço contra o teto e o ideal: ABAIXO_DO_IDEAL, ENTRE_IDEAL_E_TETO ou ACIMA_DO_TETO.
     *
     * Sempre existe: o "Salvar" recusa campo vazio e qualquer bloqueio, então todo cálculo gravado tem teto e
     * ideal — e o preço do cálculo é obrigatório. Cálculo na lista sem teto é dado corrompido, não um estado.
     */
    static String status(BigDecimal price, BigDecimal ceiling, BigDecimal ideal) {
        Objects.requireNonNull(price, "preço de referência ausente num cálculo gravado");
        Objects.requireNonNull(ceiling, "preço teto ausente num cálculo gravado");
        Objects.requireNonNull(ideal, "preço ideal ausente num cálculo gravado");
        if (price.compareTo(ideal) <= 0) {
            return "ABAIXO_DO_IDEAL";
        }
        return price.compareTo(ceiling) <= 0 ? "ENTRE_IDEAL_E_TETO" : "ACIMA_DO_TETO";
    }

    // ------------------------------------------------------------------ escrita

    @Transactional
    public Detail create(UUID userId, CeilingPriceInput in) {
        CeilingPriceDefaults.AssetInfo a = defaults.asset(in.ticker());
        if (repository.findByUserIdAndAssetId(userId, a.id()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um cálculo de preço teto para " + a.ticker() + ": abra-o para editar.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        Asset asset = assetRepository.findById(a.id()).orElseThrow();
        CeilingPrice cp = CeilingPrice.builder().user(user).asset(asset).build();
        Defaults d = defaults.of(a.ticker(), null);
        apply(cp, in, d);
        cp = repository.save(cp);
        return toDetail(cp, defaults.of(a.ticker(), cp.getId()));
    }

    @Transactional
    public Detail update(UUID userId, UUID id, CeilingPriceInput in) {
        CeilingPrice cp = owned(userId, id);
        if (in.ticker() != null && !in.ticker().trim().equalsIgnoreCase(cp.getAsset().getTicker())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O ativo de um cálculo não muda.");
        }
        Defaults d = defaults.of(cp.getAsset().getTicker(), cp.getId());
        CeilingPriceInput withTicker = new CeilingPriceInput(cp.getAsset().getTicker(), in.quarters(), in.shares(),
                in.roePct(), in.payoutPct(), in.discountRatePct(), in.perpetualGrowthPct(), in.safetyMarginPct(),
                in.price(), in.notes(), in.confirmWarnings());
        apply(cp, withTicker, d);
        cp = repository.save(cp);
        // Salvo de novo com os padroes de hoje: o "padrao mudou" acabou (docs/planos/PLANO_NOTIFICACOES.md, 5.6).
        if (alerts != null) {
            alerts.resolveDefaultsChanged(cp);
        }
        return toDetail(cp, d);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(owned(userId, id));
    }

    /**
     * Liga/desliga o alerta de preço abaixo do ideal. Ligado com o preço já abaixo: avisa na próxima atualização do
     * preço (o estado começa "acima").
     */
    @Transactional
    public boolean setAlert(UUID userId, UUID id, boolean enabled) {
        CeilingPrice cp = owned(userId, id);
        cp.setAlertEnabled(enabled);
        cp.setAlertBelowIdeal(false);
        repository.save(cp);
        return enabled;
    }

    /** O ideal do cálculo gravado e o preço atual do ativo (o mesmo "hoje" da lista). Nulo sem preço. */
    public record PriceCheck(BigDecimal ideal, BigDecimal price, LocalDate priceDate) {
    }

    public PriceCheck priceCheck(CeilingPrice cp) {
        Defaults d = defaults.of(cp.getAsset().getTicker(), cp.getId());
        CeilingPriceRules.Result r = CeilingPriceRules.calculate(fromEntity(cp).inputs(), limits);
        BigDecimal price = d == null || d.price() == null ? null : d.price().value();
        return new PriceCheck(perShare(r.idealPrice()), price, d == null ? null : d.priceDate());
    }

    /** Campos de balanço cujo padrão mudou desde o "Salvar" (D5: preço e Selic ficam de fora). */
    public List<String> changedBalanceDefaults(CeilingPrice cp) {
        Defaults d = defaults.of(cp.getAsset().getTicker(), cp.getId());
        return changedDefaults(cp, d).stream()
                .filter(f -> f.equals("quarters") || f.equals("shares") || f.equals("roePct") || f.equals("payoutPct"))
                .toList();
    }

    /** O último trimestre que o catálogo tem para o ativo (a chave do "padrão mudou"). */
    public LocalDate latestQuarterEnd(CeilingPrice cp) {
        return defaults.of(cp.getAsset().getTicker(), cp.getId()).latestQuarterEnd();
    }

    /** Valida (obrigatórios, bloqueios, confirmação dos alertas) e copia as entradas e a origem de cada uma. */
    private void apply(CeilingPrice cp, CeilingPriceInput in, Defaults d) {
        List<Issue> missing = new ArrayList<>();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("shares", in.shares());
        values.put("roePct", in.roePct());
        values.put("payoutPct", in.payoutPct());
        values.put("discountRatePct", in.discountRatePct());
        values.put("perpetualGrowthPct", in.perpetualGrowthPct());
        values.put("safetyMarginPct", in.safetyMarginPct());
        values.put("price", in.price());
        values.forEach((k, v) -> {
            if (v == null) {
                missing.add(new Issue("CAMPO_VAZIO", Level.BLOQUEIO, k, label(k) + " vazio: informe o valor."));
            }
        });
        if (in.quarters() == null || in.quarters().size() != 4) {
            missing.add(new Issue("TRIMESTRE_AUSENTE", Level.BLOQUEIO, "quarters",
                    "São necessários os lucros de quatro trimestres."));
        }
        if (!missing.isEmpty()) {
            throw new Rejected("Preencha todos os campos antes de salvar.", missing);
        }
        Normalized n = normalize(in);
        Result r = result(n, d);
        List<Issue> blocking = r.issues().stream().filter(i -> i.level() == Level.BLOQUEIO).toList();
        if (!blocking.isEmpty()) {
            throw new Rejected("O cálculo tem bloqueios e não pode ser salvo.", blocking);
        }
        List<Issue> warnings = r.issues().stream().filter(i -> i.level() == Level.ATENCAO).toList();
        if (!warnings.isEmpty() && !in.confirmWarnings()) {
            throw new Rejected("Há alertas: confirme para salvar mesmo assim.", warnings);
        }

        cp.setShares(in.shares());
        cp.setRoePct(in.roePct());
        cp.setPayoutPct(in.payoutPct());
        cp.setDiscountRatePct(in.discountRatePct());
        cp.setPerpetualGrowthPct(in.perpetualGrowthPct());
        cp.setSafetyMarginPct(in.safetyMarginPct());
        cp.setPrice(in.price());
        cp.setNotes(in.notes() == null || in.notes().isBlank() ? null : in.notes().trim());
        cp.setCalculatedAt(LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo")));
        // O ideal pode ter mudado: o alerta reavalia na proxima atualizacao do preco (secao 5.5).
        cp.setAlertBelowIdeal(false);

        Map<String, InputOrigin> origins = new LinkedHashMap<>();
        Map<String, DefaultValue> defs = defaultsByField(d);
        values.forEach((k, v) -> {
            DefaultValue dv = defs.get(k);
            BigDecimal value = v instanceof Long l ? BigDecimal.valueOf(l) : (BigDecimal) v;
            boolean isDefault = dv != null && CeilingPriceDefaults.same(dv.value(), value);
            origins.put(k, new InputOrigin(isDefault ? CeilingPriceQuarter.PADRAO : CeilingPriceQuarter.INFORMADO,
                    dv == null ? null : dv.ref(), dv == null ? null : dv.value()));
        });
        try {
            cp.setInputsOrigin(json.writeValueAsString(origins));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        cp.getQuarters().clear();
        for (int i = 0; i < 4; i++) {
            QuarterInput q = in.quarters().get(i);
            boolean isDefault = !INFORMADO(n.quarterOrigins().get(i));
            cp.getQuarters().add(CeilingPriceQuarter.builder()
                    .ceilingPrice(cp).position((short) (i + 1)).periodEnd(q.periodEnd())
                    .originalValue(q.originalValue().setScale(2, RoundingMode.HALF_UP))
                    .unit(n.units().get(i)).currency(n.currencies().get(i)).fxRate(n.fxRates().get(i))
                    .valueBrl(n.inputs().quarters().get(i))
                    .origin(isDefault ? CeilingPriceQuarter.PADRAO : CeilingPriceQuarter.INFORMADO)
                    .build());
        }
    }

    private static boolean INFORMADO(String origin) {
        return CeilingPriceQuarter.INFORMADO.equals(origin);
    }

    // ------------------------------------------------------------------ normalização das entradas

    /** Entradas já em reais, com rótulos e a origem de cada lucro (igual ao da CVM = PADRAO). */
    record Normalized(Inputs inputs, List<LocalDate> periods, List<String> labels, List<String> units,
                      List<String> currencies, List<BigDecimal> fxRates, List<String> quarterOrigins,
                      List<Issue> issues) {
    }

    private Normalized normalize(CeilingPriceInput in) {
        List<BigDecimal> values = new ArrayList<>();
        List<LocalDate> periods = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> units = new ArrayList<>();
        List<String> currencies = new ArrayList<>();
        List<BigDecimal> rates = new ArrayList<>();
        List<String> origins = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        List<QuarterInput> qs = in.quarters() == null ? List.of() : in.quarters();
        for (int i = 0; i < qs.size() && i < 4; i++) {
            QuarterInput q = qs.get(i);
            periods.add(q.periodEnd());
            String label = q.periodEnd() == null ? "T" + (i == 3 ? "" : "-" + (3 - i))
                    : CeilingPriceDefaults.label(q.periodEnd());
            labels.add(label);
            String unit = q.unit() == null ? "UNIDADE" : q.unit().trim().toUpperCase();
            String currency = q.currency() == null ? "BRL" : q.currency().trim().toUpperCase();
            units.add(unit);
            currencies.add(currency);
            BigDecimal rate = BigDecimal.ONE;
            BigDecimal brl = null;
            if (q.originalValue() != null && q.periodEnd() != null) {
                try {
                    MoneyNormalizer.Unit u = MoneyNormalizer.Unit.valueOf(unit);
                    if (!defaults.acceptsCurrency(currency)) {
                        throw new IllegalArgumentException("moeda fora do cadastro: " + currency);
                    }
                    boolean base = defaults.isBaseCurrency(currency);
                    if (!base) {
                        rate = q.fxRate() != null ? q.fxRate() : defaults.fxRate(currency, q.periodEnd());
                        if (rate == null) {
                            issues.add(new Issue("CAMBIO_AUSENTE", Level.BLOQUEIO, "quarters[" + i + "]",
                                    "Câmbio (PTAX) não encontrado para " + CeilingPriceDefaults.fmt(q.periodEnd())
                                            + ": informe a taxa."));
                        }
                    }
                    if (rate != null) {
                        brl = MoneyNormalizer.toBrl(q.originalValue(), u, base, rate);
                    }
                } catch (IllegalArgumentException e) {
                    issues.add(new Issue("UNIDADE_INVALIDA", Level.BLOQUEIO, "quarters[" + i + "]",
                            "Unidade ou moeda inválida no " + label + " (" + unit + ", " + currency + ")."));
                }
            }
            rates.add(rate == null ? BigDecimal.ONE : rate);
            values.add(brl);
            origins.add(null);
        }
        while (values.size() < 4) {
            values.add(null);
            periods.add(null);
            labels.add("T" + (values.size() == 4 ? "" : "-" + (4 - values.size())));
            units.add("UNIDADE");
            currencies.add("BRL");
            rates.add(BigDecimal.ONE);
            origins.add(null);
        }
        Inputs inputs = new Inputs(values, labels, in.shares(), in.roePct(), in.payoutPct(), in.discountRatePct(),
                in.perpetualGrowthPct(), in.safetyMarginPct(), in.price());
        return new Normalized(inputs, periods, labels, units, currencies, rates, origins, issues);
    }

    private Normalized fromEntity(CeilingPrice cp) {
        List<BigDecimal> values = new ArrayList<>();
        List<LocalDate> periods = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> units = new ArrayList<>();
        List<String> currencies = new ArrayList<>();
        List<BigDecimal> rates = new ArrayList<>();
        List<String> origins = new ArrayList<>();
        for (CeilingPriceQuarter q : cp.getQuarters()) {
            values.add(q.getValueBrl());
            periods.add(q.getPeriodEnd());
            labels.add(CeilingPriceDefaults.label(q.getPeriodEnd()));
            units.add(q.getUnit());
            currencies.add(q.getCurrency());
            rates.add(q.getFxRate());
            origins.add(q.getOrigin());
        }
        Inputs inputs = new Inputs(values, labels, cp.getShares(), cp.getRoePct(), cp.getPayoutPct(),
                cp.getDiscountRatePct(), cp.getPerpetualGrowthPct(), cp.getSafetyMarginPct(), cp.getPrice());
        return new Normalized(inputs, periods, labels, units, currencies, rates, origins, List.of());
    }

    // ------------------------------------------------------------------ resultado e alertas do contexto

    private Result result(Normalized n, Defaults d) {
        CeilingPriceRules.Result r = CeilingPriceRules.calculate(n.inputs(), limits);
        List<Issue> issues = new ArrayList<>(n.issues());
        issues.addAll(r.issues());
        issues.addAll(contextIssues(n, d, r));
        // origem de cada lucro: igual ao da CVM no mesmo trimestre, em reais = PADRAO
        for (int i = 0; i < 4; i++) {
            DefaultQuarter dq = defaultQuarter(d, n.periods().get(i));
            BigDecimal v = n.inputs().quarters().get(i);
            boolean isDefault = dq != null && v != null && dq.value() != null && "BRL".equals(n.currencies().get(i))
                    && v.compareTo(dq.value()) == 0;
            n.quarterOrigins().set(i, isDefault ? CeilingPriceQuarter.PADRAO : CeilingPriceQuarter.INFORMADO);
        }
        issues.sort(Comparator.comparing(Issue::level));
        return toResult(n, r, issues);
    }

    private List<Issue> contextIssues(Normalized n, Defaults d, CeilingPriceRules.Result r) {
        List<Issue> out = new ArrayList<>();
        Inputs in = n.inputs();
        // N longe do padrão
        if (in.shares() != null && d.shares().value() != null && d.shares().value().signum() > 0) {
            BigDecimal dev = deviationPct(BigDecimal.valueOf(in.shares()), d.shares().value());
            if (dev.compareTo(sharesDeviationPct) > 0) {
                out.add(new Issue("PREMISSA_EXTREMA", Level.ATENCAO, "shares", "Quantidade de ações "
                        + number(dev) + "% diferente do padrão (" + integer(d.shares().value()) + "). Confira."));
            }
        }
        // lucro digitado longe do da CVM
        List<LocalDate> periods = n.periods();
        for (int i = 0; i < 4; i++) {
            LocalDate p = periods.get(i);
            DefaultQuarter dq = defaultQuarter(d, p);
            BigDecimal v = in.quarters().get(i);
            if (dq != null && dq.value() != null && v != null && dq.value().signum() != 0) {
                BigDecimal dev = deviationPct(v, dq.value());
                if (dev.compareTo(quarterDeviationPct) > 0) {
                    out.add(new Issue("PREMISSA_EXTREMA", Level.ATENCAO, "quarters[" + i + "]", "Lucro do "
                            + n.labels().get(i) + " " + number(dev) + "% diferente do da CVM ("
                            + CeilingPriceRules.money(dq.value()) + "). Confira."));
                }
            }
        }
        // lucro muito baixo em relação ao patrimônio
        if (r.netIncomeTtm() != null && r.netIncomeTtm().signum() > 0 && d.equityParent() != null
                && d.equityParent().signum() > 0) {
            BigDecimal ratio = r.netIncomeTtm().divide(d.equityParent(), MC).multiply(HUNDRED);
            if (ratio.compareTo(lowProfitPct) < 0) {
                out.add(new Issue("LL_TTM_BAIXO", Level.ATENCAO, "netIncomeTtm", "Lucro dos 12 meses é "
                        + number(ratio) + "% do patrimônio da controladora (abaixo de " + number(lowProfitPct)
                        + "%): resultado pouco representativo."));
            }
        }
        // reapresentação: a janela padrão inteira, e o TTM do catálogo diferente da soma
        boolean defaultWindow = d.quarters().size() == 4;
        for (int i = 0; defaultWindow && i < 4; i++) {
            DefaultQuarter dq = d.quarters().get(i);
            defaultWindow = dq.value() != null && dq.periodEnd().equals(periods.get(i))
                    && CeilingPriceDefaults.same(dq.value(), in.quarters().get(i));
        }
        if (defaultWindow && d.catalogTtm() != null && r.netIncomeTtm() != null
                && d.catalogTtm().compareTo(r.netIncomeTtm()) != 0) {
            out.add(new Issue("REAPRESENTACAO", Level.INFORMACAO, "netIncomeTtm", "O lucro de 12 meses do balanço ("
                    + CeilingPriceRules.money(d.catalogTtm()) + ") difere da soma dos 4 trimestres: um balanço "
                    + "seguinte reapresentou um trimestre. Vale a soma."));
        }
        // balanço mais novo que a janela digitada
        if (periods.get(3) != null && d.latestQuarterEnd() != null
                && d.latestQuarterEnd().isAfter(periods.get(3))) {
            out.add(new Issue("TRIMESTRE_DESATUALIZADO", Level.INFORMACAO, "quarters", "Há balanço mais recente ("
                    + CeilingPriceDefaults.label(d.latestQuarterEnd()) + "): use \"Atualizar trimestres\"."));
        }
        LocalDate today = defaults.today();
        if (d.latestQuarterEnd() != null && ChronoUnit.DAYS.between(d.latestQuarterEnd(), today) > staleBalanceDays) {
            out.add(new Issue("BALANCO_DEFASADO", Level.ATENCAO, null, "Último balanço da companhia: "
                    + CeilingPriceDefaults.fmt(d.latestQuarterEnd()) + "."));
        }
        if (in.price() != null && CeilingPriceDefaults.same(in.price(), d.price().value()) && d.priceDate() != null
                && ChronoUnit.DAYS.between(d.priceDate(), today) > stalePriceDays) {
            out.add(new Issue("PRECO_DEFASADO", Level.ATENCAO, "price", "Preço de "
                    + CeilingPriceDefaults.fmt(d.priceDate()) + " (último disponível)."));
        }
        if (d.roePct().note() != null && d.roePct().value() != null && CeilingPriceDefaults.same(in.roePct(), d.roePct().value())) {
            out.add(new Issue("MEDIA_INCOMPLETA", Level.ATENCAO, "roePct", "ROE: " + d.roePct().note() + "."));
        }
        if (d.payoutPct().note() != null && d.payoutPct().value() != null
                && CeilingPriceDefaults.same(in.payoutPct(), d.payoutPct().value())) {
            out.add(new Issue("MEDIA_INCOMPLETA", Level.ATENCAO, "payoutPct", "Payout: " + d.payoutPct().note() + "."));
        }
        return out;
    }

    private static BigDecimal deviationPct(BigDecimal value, BigDecimal reference) {
        return value.subtract(reference).abs().divide(reference.abs(), MC).multiply(HUNDRED);
    }

    private Result toResult(Normalized n, CeilingPriceRules.Result r, List<Issue> issues) {
        return new Result(money(r.netIncomeTtm()), pct(r.growth()), money(r.fc1()), money(r.pv1()), money(r.fc2()),
                money(r.pv2()), money(r.fc3()), money(r.pv3()), money(r.terminalValue()), money(r.terminalPv()),
                money(r.equityValue()), pct(r.terminalWeight()), perShare(r.ceilingPrice()),
                perShare(r.idealPrice()), pct(r.ceilingMargin()), pct(r.idealMargin()), steps(n.inputs(), r),
                List.copyOf(issues));
    }

    /** Memória de cálculo: cada passo com a fórmula e a fórmula com os números (passo 9.7 do pedido). */
    static List<Step> steps(Inputs in, CeilingPriceRules.Result r) {
        List<Step> s = new ArrayList<>();
        String d = in.discountRatePct() == null ? "d" : "(1 + " + num(in.discountRatePct().divide(HUNDRED, MC)) + ")";
        String g = r.growth() == null ? "(1 + g)" : "(1 + " + num(r.growth()) + ")";
        String bigG = in.perpetualGrowthPct() == null ? "G" : num(in.perpetualGrowthPct().divide(HUNDRED, MC));
        String dd = in.discountRatePct() == null ? "d" : num(in.discountRatePct().divide(HUNDRED, MC));
        List<String> qs = new ArrayList<>();
        for (BigDecimal q : in.quarters()) {
            qs.add(q == null ? "?" : num(q));
        }
        s.add(new Step("netIncomeTtm", "LL TTM", money(r.netIncomeTtm()), "LL T-3 + LL T-2 + LL T-1 + LL T",
                String.join(" + ", qs) + " = " + num(r.netIncomeTtm())));
        s.add(new Step("growth", "Crescimento sustentável (g)", pct(r.growth()), "ROE × (1 − Payout)",
                (in.roePct() == null ? "?" : num(in.roePct()) + "%") + " × (1 − "
                        + (in.payoutPct() == null ? "?" : num(in.payoutPct()) + "%") + ") = "
                        + (r.growth() == null ? "—" : num(r.growth().multiply(HUNDRED)) + "%")));
        s.add(new Step("fc1", "FC1", money(r.fc1()), "FC1 = LL TTM", "FC1 = " + num(r.fc1())));
        s.add(new Step("pv1", "VPL1", money(r.pv1()), "FC1 ÷ (1 + d)", num(r.fc1()) + " ÷ " + d + " = " + num(r.pv1())));
        s.add(new Step("fc2", "FC2", money(r.fc2()), "FC1 × (1 + g)", num(r.fc1()) + " × " + g + " = " + num(r.fc2())));
        s.add(new Step("pv2", "VPL2", money(r.pv2()), "FC2 ÷ (1 + d)²", num(r.fc2()) + " ÷ " + d + "² = " + num(r.pv2())));
        s.add(new Step("fc3", "FC3", money(r.fc3()), "FC2 × (1 + g)", num(r.fc2()) + " × " + g + " = " + num(r.fc3())));
        s.add(new Step("pv3", "VPL3", money(r.pv3()), "FC3 ÷ (1 + d)³", num(r.fc3()) + " ÷ " + d + "³ = " + num(r.pv3())));
        s.add(new Step("terminalValue", "Valor terminal", money(r.terminalValue()), "FC3 × (1 + G) ÷ (d − G)",
                num(r.fc3()) + " × (1 + " + bigG + ") ÷ (" + dd + " − " + bigG + ") = " + num(r.terminalValue())));
        s.add(new Step("terminalPv", "VPL do valor terminal", money(r.terminalPv()), "Valor terminal ÷ (1 + d)³",
                num(r.terminalValue()) + " ÷ " + d + "³ = " + num(r.terminalPv())));
        s.add(new Step("equityValue", "Valor do patrimônio", money(r.equityValue()),
                "VPL1 + VPL2 + VPL3 + VPL do valor terminal", num(r.pv1()) + " + " + num(r.pv2()) + " + "
                + num(r.pv3()) + " + " + num(r.terminalPv()) + " = " + num(r.equityValue())));
        s.add(new Step("ceilingPrice", "Preço Teto", perShare(r.ceilingPrice()), "Valor do patrimônio ÷ N",
                num(r.equityValue()) + " ÷ " + (in.shares() == null ? "?" : num(BigDecimal.valueOf(in.shares())))
                        + " = " + num(r.ceilingPrice())));
        s.add(new Step("idealPrice", "Preço Ideal", perShare(r.idealPrice()), "Preço Teto × (1 − MS)",
                num(r.ceilingPrice()) + " × (1 − " + (in.safetyMarginPct() == null ? "?" : num(in.safetyMarginPct()) + "%")
                        + ") = " + num(r.idealPrice())));
        s.add(new Step("ceilingMargin", "Margem do Preço Teto", pct(r.ceilingMargin()), "Preço Teto ÷ Preço − 1",
                num(r.ceilingPrice()) + " ÷ " + (in.price() == null ? "?" : num(in.price())) + " − 1 = "
                        + (r.ceilingMargin() == null ? "—" : num(r.ceilingMargin().multiply(HUNDRED)) + "%")));
        s.add(new Step("idealMargin", "Margem do Preço Ideal", pct(r.idealMargin()), "Preço Ideal ÷ Preço − 1",
                num(r.idealPrice()) + " ÷ " + (in.price() == null ? "?" : num(in.price())) + " − 1 = "
                        + (r.idealMargin() == null ? "—" : num(r.idealMargin().multiply(HUNDRED)) + "%")));
        return s;
    }

    // ------------------------------------------------------------------ detalhe

    private Detail toDetail(CeilingPrice cp, Defaults d) {
        Normalized n = fromEntity(cp);
        CeilingPriceRules.Result raw = CeilingPriceRules.calculate(n.inputs(), limits);
        List<Issue> issues = new ArrayList<>(raw.issues());
        issues.addAll(contextIssues(n, d, raw));
        issues.sort(Comparator.comparing(Issue::level));
        Result r = toResult(n, raw, issues);
        List<QuarterView> qs = new ArrayList<>();
        for (CeilingPriceQuarter q : cp.getQuarters()) {
            qs.add(new QuarterView(q.getPosition(), q.getPeriodEnd(), CeilingPriceDefaults.label(q.getPeriodEnd()),
                    q.getOriginalValue(), q.getUnit(), q.getCurrency(), q.getFxRate(), q.getValueBrl(), q.getOrigin()));
        }
        return new Detail(cp.getId(), cp.getAsset().getTicker(), cp.getAsset().getName(),
                cp.getAsset().getIssuer() != null ? cp.getAsset().getIssuer().getId() : null, d.hasLogo(), qs,
                cp.getShares(), cp.getRoePct(), cp.getPayoutPct(), cp.getDiscountRatePct(), cp.getPerpetualGrowthPct(),
                cp.getSafetyMarginPct(), cp.getPrice(), cp.getNotes(), origins(cp), cp.getCalculatedAt(), r, d,
                changedDefaults(cp, d));
    }

    private Map<String, InputOrigin> origins(CeilingPrice cp) {
        try {
            return json.readValue(cp.getInputsOrigin(), new TypeReference<LinkedHashMap<String, InputOrigin>>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    /**
     * Campos cujo padrão mudou desde o "Salvar" (o ↻ fica destacado): o padrão gravado é diferente do de hoje.
     * {@code quarters}: saiu balanço mais novo que a janela gravada, ou a CVM mudou um lucro que veio dela.
     */
    List<String> changedDefaults(CeilingPrice cp, Defaults d) {
        List<String> out = new ArrayList<>();
        Map<String, InputOrigin> origins = origins(cp);
        Map<String, DefaultValue> now = defaultsByField(d);
        for (String f : FIELDS) {
            InputOrigin o = origins.get(f);
            DefaultValue dv = now.get(f);
            if (o != null && dv != null && !CeilingPriceDefaults.same(o.defaultValue(), dv.value())) {
                out.add(f);
            }
        }
        boolean quarters = false;
        if (!cp.getQuarters().isEmpty() && d.latestQuarterEnd() != null
                && d.latestQuarterEnd().isAfter(cp.getQuarters().get(cp.getQuarters().size() - 1).getPeriodEnd())) {
            quarters = true;
        }
        for (CeilingPriceQuarter q : cp.getQuarters()) {
            DefaultQuarter dq = defaultQuarter(d, q.getPeriodEnd());
            if (CeilingPriceQuarter.PADRAO.equals(q.getOrigin()) && dq != null
                    && !CeilingPriceDefaults.same(dq.value(), q.getValueBrl())) {
                quarters = true;
            }
        }
        if (quarters) {
            out.add("quarters");
        }
        return out;
    }

    // ------------------------------------------------------------------ apoio

    private CeilingPrice owned(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Preço teto não encontrado"));
    }

    private static Map<String, DefaultValue> defaultsByField(Defaults d) {
        Map<String, DefaultValue> m = new HashMap<>();
        m.put("shares", d.shares());
        m.put("roePct", d.roePct());
        m.put("payoutPct", d.payoutPct());
        m.put("discountRatePct", d.discountRatePct());
        m.put("perpetualGrowthPct", d.perpetualGrowthPct());
        m.put("safetyMarginPct", d.safetyMarginPct());
        m.put("price", d.price());
        return m;
    }

    private static DefaultQuarter defaultQuarter(Defaults d, LocalDate periodEnd) {
        if (periodEnd == null) {
            return null;
        }
        // a janela padrão só traz 4; trimestre mais antigo não tem padrão aqui
        return d.quarters().stream().filter(q -> q.periodEnd().equals(periodEnd)).findFirst().orElse(null);
    }

    /**
     * Preço médio da carteira nas ações da lista, por id do ativo: só as posições delas, não a Carteira inteira
     * (docs/planos/PLANO_PERFORMANCE.md, P3, D4).
     */
    private Map<Long, BigDecimal> averagePrices(UUID userId, List<CeilingPrice> all) {
        try {
            return portfolioService.averagePrices(userId, all.stream().map(cp -> cp.getAsset().getId()).toList());
        } catch (RuntimeException e) {
            // sem carteira: a coluna fica vazia
            return Map.of();
        }
    }

    private static String label(String field) {
        return switch (field) {
            case "shares" -> "Quantidade de ações";
            case "roePct" -> "ROE";
            case "payoutPct" -> "Payout";
            case "discountRatePct" -> "Taxa de desconto";
            case "perpetualGrowthPct" -> "Crescimento na perpetuidade";
            case "safetyMarginPct" -> "Margem de segurança";
            case "price" -> "Preço";
            default -> field;
        };
    }

    static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal perShare(BigDecimal v) {
        return v == null ? null : v.setScale(4, RoundingMode.HALF_UP);
    }

    /** Fração → pontos com 4 casas. */
    static BigDecimal pct(BigDecimal fraction) {
        return fraction == null ? null : fraction.multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
    }

    private static String number(BigDecimal v) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"))).format(v);
    }

    private static String integer(BigDecimal v) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"))).format(v);
    }

    /** Número da memória de cálculo: até 6 casas, padrão brasileiro. */
    static String num(BigDecimal v) {
        if (v == null) {
            return "—";
        }
        return new DecimalFormat("#,##0.######", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")))
                .format(v);
    }
}

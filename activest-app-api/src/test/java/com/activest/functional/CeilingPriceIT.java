package com.activest.functional;

import com.activest.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import com.activest.domain.ceilingprice.CeilingPriceAlertService;

/**
 * Preço Teto de ponta a ponta (docs/planos/PLANO_PRECO_TETO.md, 8.1): padrões do catálogo, cálculo sem gravar,
 * gravação com as validações, origem de cada entrada, lista com as três comparações e o dono.
 * Balanços sintéticos da Petrobras (emissor real do seed), em reais.
 */
class CeilingPriceIT extends FunctionalBase {

    static final String PETR = "33000167000101";
    static final String BASE = "/api/v1/ceiling-prices";

    private User user;
    private BigDecimal petr4PriceBefore;
    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @BeforeEach
    void scenario() {
        user = userWithPortfolio("precoteto@activest.test");
        petr4PriceBefore = jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = 'PETR4'", BigDecimal.class);
        jdbc.update("UPDATE assets SET current_price = NULL WHERE ticker = 'PETR4'");
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker = 'PETR4')");
        long petr = jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, PETR);

        // exercícios fechados (ROE com PL médio; payout = pagos ÷ lucro)
        doc(petr, "DFP", "2022-12-31", null, "188000000000", "350000000000", "40000000000");
        doc(petr, "DFP", "2023-12-31", null, "125000000000", "370000000000", "50000000000");
        doc(petr, "DFP", "2024-12-31", null, "36600000000", "366000000000", "60000000000");
        // os quatro trimestres mais recentes (lucro da controladora isolado)
        doc(petr, "ITR", "2025-09-30", "32700000000", "99000000000", "400000000000", "40000000000");
        doc(petr, "DFP", "2025-12-31", "15500000000", "110129000000", "415786000000", "45205000000");
        doc(petr, "ITR", "2026-03-31", "35200000000", "105000000000", "420000000000", "44000000000");
        doc(petr, "ITR", "2026-06-30", "26700000000", "110100000000", "425000000000", "43000000000");

        jdbc.update("""
                INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
                SELECT id, ?, 47.99, 'B3_COTAHIST' FROM assets WHERE ticker = 'PETR4'""",
                java.sql.Date.valueOf(today.minusDays(1)));
        index("SELIC_META", today.minusDays(5), "13.75");
        index("USD_PTAX", LocalDate.of(2026, 6, 30), "5.4321");
        // o cambio do lucro em dolar vem do cadastro de moedas (docs/planos/PLANO_CADASTRO_MOEDAS.md, D9)
        jdbc.update("DELETE FROM exchange_rates WHERE currency_code = 'USD'");
        jdbc.update("INSERT INTO exchange_rates (currency_code, reference_date, buy_rate, sell_rate) "
                + "VALUES ('USD', '2026-06-30', 5.4315, 5.4321)");
        exchangeRates.evict();
    }

    @AfterEach
    void restores() {
        jdbc.update("UPDATE assets SET current_price = ? WHERE ticker = 'PETR4'", petr4PriceBefore);
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker = 'PETR4')");
    }

    private void doc(long issuer, String type, String data, String quarterlyEarnings, String earnings12m, String pl, String paid12m) {
        Long id = jdbc.queryForObject("""
                INSERT INTO cvm_documents (issuer_id, doc_type, reference_date, version, consolidated, received_at)
                VALUES (?, ?, CAST(? AS DATE), 1, true, CAST(? AS DATE) + 40) RETURNING id""",
                Long.class, issuer, type, data, data);
        jdbc.update("""
                INSERT INTO company_quarters (issuer_id, period_end, source_doc_id, net_income_parent_q,
                    net_income_parent_ttm, equity_parent, shares_outstanding, dividends_paid_ttm)
                VALUES (?, CAST(? AS DATE), ?, ?, ?, ?, 12888732761, ?)""",
                issuer, data, id, quarterlyEarnings == null ? null : new BigDecimal(quarterlyEarnings), new BigDecimal(earnings12m),
                new BigDecimal(pl), new BigDecimal(paid12m));
    }

    private void index(String code, LocalDate data, String value) {
        jdbc.update("""
                INSERT INTO market_index_values (index_id, reference_date, value)
                SELECT id, ?, ? FROM market_indices WHERE code = ?""", java.sql.Date.valueOf(data),
                new BigDecimal(value), code);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> defaults(String ticker) {
        ResponseEntity<Map> r = get(user, BASE + "/defaults/" + ticker, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal value(Map<String, Object> m, String field) {
        Object v = ((Map<String, Object>) m.get(field)).get("value");
        return v == null ? null : new BigDecimal(v.toString());
    }

    /** O formulário como abre: tudo no padrão. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> defaultForm(Map<String, Object> d) {
        List<Map<String, Object>> qs = new ArrayList<>();
        for (Map<String, Object> q : (List<Map<String, Object>>) d.get("quarters")) {
            Map<String, Object> m = new HashMap<>();
            m.put("periodEnd", q.get("periodEnd"));
            m.put("originalValue", q.get("value"));
            m.put("unit", "UNIDADE");
            m.put("currency", "BRL");
            qs.add(m);
        }
        Map<String, Object> f = new HashMap<>();
        f.put("ticker", "PETR4");
        f.put("quarters", qs);
        f.put("shares", value(d, "shares").longValue());
        for (String c : List.of("roePct", "payoutPct", "discountRatePct", "perpetualGrowthPct", "safetyMarginPct",
                "price")) {
            f.put(c, value(d, c));
        }
        return f;
    }

    @SuppressWarnings("unchecked")
    private static List<String> codes(Map<String, Object> body) {
        Object issues = body.containsKey("issues") ? body.get("issues") : ((Map<String, Object>) body.get("result")).get("issues");
        return ((List<Map<String, Object>>) issues).stream().map(i -> (String) i.get("code")).toList();
    }

    @Test
    @DisplayName("Padrões: 4 trimestres do catálogo, ações sem tesouraria, médias de 3 exercícios, Selic meta, 3% e 15%")
    @SuppressWarnings("unchecked")
    void catalogDefaults() {
        Map<String, Object> d = defaults("PETR4");

        List<Map<String, Object>> qs = (List<Map<String, Object>>) d.get("quarters");
        assertThat(qs).extracting(q -> q.get("label")).containsExactly("3T25", "4T25", "1T26", "2T26");
        assertThat(number(qs.get(1).get("value"))).isEqualByComparingTo("15500000000");
        assertThat((String) qs.get(1).get("ref")).contains("CVM DFP 31/12/2025");
        assertThat(value(d, "shares")).isEqualByComparingTo("12888732761");
        // ROE 2023–2025: 125/360, 36,6/368, 110,129/390,893 → 34,72; 9,95; 28,17
        assertThat(value(d, "roePct")).isEqualByComparingTo("24.28");
        // payout 2023–2025: 50/125, 60/36,6, 45,205/110,129 → 40,00; 163,93; 41,05
        assertThat(value(d, "payoutPct")).isEqualByComparingTo("81.66");
        assertThat(value(d, "discountRatePct")).isEqualByComparingTo("13.75");
        assertThat(value(d, "perpetualGrowthPct")).isEqualByComparingTo("3");
        assertThat(value(d, "safetyMarginPct")).isEqualByComparingTo("15");
        assertThat(value(d, "price")).isEqualByComparingTo("47.99");
        assertThat(d.get("existingId")).isNull();
    }

    @Test
    @DisplayName("Cenário 12 pela API: a linha VALE3 da planilha no formulário dá o mesmo teto e ideal")
    @SuppressWarnings("unchecked")
    void spreadsheetThroughApi() {
        Map<String, Object> f = defaultForm(defaults("PETR4"));
        List<Map<String, Object>> qs = (List<Map<String, Object>>) f.get("quarters");
        String[] earnings = {"9956933910", "6940807500", "9956933910", "6940807500"};
        for (int i = 0; i < 4; i++) {
            qs.get(i).put("originalValue", new BigDecimal(earnings[i]));
        }
        f.put("shares", 4262140830L);
        f.put("roePct", new BigDecimal("19.26"));
        f.put("payoutPct", new BigDecimal("48.19"));
        f.put("discountRatePct", new BigDecimal("13.75"));
        f.put("price", new BigDecimal("67.63"));

        ResponseEntity<Map> r = post(user, BASE + "/calculate", f, Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> res = r.getBody();
        assertThat(number(res.get("ceilingPrice"))).isEqualByComparingTo("82.6607");
        assertThat(number(res.get("idealPrice"))).isEqualByComparingTo("70.2616");
        assertThat(number(res.get("ceilingMarginPct"))).isEqualByComparingTo("22.2248");
        assertThat(number(res.get("idealMarginPct"))).isEqualByComparingTo("3.8911");
        assertThat(number(res.get("equityValue"))).isEqualByComparingTo("352311364973.54");
        assertThat((List<Map<String, Object>>) res.get("steps")).extracting(s -> s.get("key"))
                .contains("netIncomeTtm", "growth", "pv1", "terminalValue", "ceilingPrice", "idealMargin");
        // lucros longe dos da CVM e N longe do padrão: alertas, não bloqueio
        assertThat(codes(Map.of("issues", res.get("issues")))).contains("PREMISSA_EXTREMA");
    }

    @Test
    @DisplayName("Salvar com os padrões: origens PADRAO, detalhe devolve o gravado, lista com as comparações e preço médio")
    @SuppressWarnings("unchecked")
    void savesWithDefaults() {
        recordSuccessfully(user, buy("PETR4", today.minusDays(20).toString(), "100", "40.00"));
        Map<String, Object> f = defaultForm(defaults("PETR4"));
        f.put("confirmWarnings", true);   // o payout acima de 100% em 2024 não gera alerta na média, mas pode haver outros

        ResponseEntity<Map> r = post(user, BASE, f, Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> det = r.getBody();
        Map<String, Map<String, Object>> origins = (Map<String, Map<String, Object>>) det.get("origins");
        assertThat(origins.values()).extracting(o -> o.get("origin")).containsOnly("PADRAO");
        assertThat(origins.get("discountRatePct").get("ref")).asString().contains("Selic meta");
        assertThat((List<Map<String, Object>>) det.get("quarters")).extracting(q -> q.get("origin"))
                .containsOnly("PADRAO");
        String id = (String) det.get("id");

        Map<String, Object> read = get(user, BASE + "/" + id, Map.class).getBody();
        assertThat(number(read.get("discountRatePct"))).isEqualByComparingTo("13.75");
        assertThat((List<String>) read.get("changedDefaults")).isEmpty();
        assertThat(((Map<String, Object>) read.get("result")).get("ceilingPrice")).isNotNull();

        List<Map<String, Object>> list = get(user, BASE, List.class).getBody();
        assertThat(list).hasSize(1);
        Map<String, Object> row = list.get(0);
        assertThat(row.get("ticker")).isEqualTo("PETR4");
        assertThat(number(row.get("safetyMarginPct"))).isEqualByComparingTo("15");
        assertThat(number(row.get("todayPrice"))).isEqualByComparingTo("47.99");
        assertThat(number(row.get("averagePrice"))).isEqualByComparingTo("40.00");
        assertThat(row.get("averageCeilingMarginPct")).isNotNull();
        assertThat(row.get("status")).isIn("ABAIXO_DO_IDEAL", "ENTRE_IDEAL_E_TETO", "ACIMA_DO_TETO");

        // o padrão muda (Selic nova): o gravado fica, o ↻ é sinalizado
        index("SELIC_META", today, "14.25");
        Map<String, Object> after = get(user, BASE + "/" + id, Map.class).getBody();
        assertThat(number(after.get("discountRatePct"))).isEqualByComparingTo("13.75");
        assertThat((List<String>) after.get("changedDefaults")).contains("discountRatePct");
        assertThat(get(user, BASE, List.class).getBody()).extracting(l -> ((Map<String, Object>) l).get("defaultsChanged"))
                .containsExactly(true);

        // já existe: criar de novo é conflito; os padrões trazem o id
        assertThat(post(user, BASE, f, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(defaults("PETR4").get("existingId")).isEqualTo(id);
    }

    @Test
    @DisplayName("Campo vazio, d ≤ G e alertas sem confirmação: não salva (422)")
    @SuppressWarnings("unchecked")
    void validationsOnSave() {
        Map<String, Object> d = defaults("PETR4");

        Map<String, Object> empty = defaultForm(d);
        empty.put("roePct", null);
        ResponseEntity<Map> r1 = post(user, BASE, empty, Map.class);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codes(r1.getBody())).containsExactly("CAMPO_VAZIO");

        Map<String, Object> dLessThanG = defaultForm(d);
        dLessThanG.put("discountRatePct", new BigDecimal("3"));
        ResponseEntity<Map> r2 = post(user, BASE, dLessThanG, Map.class);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codes(r2.getBody())).contains("DESCONTO_MENOR_IGUAL_G");

        // lucro do 2T26 em milhões e longe do da CVM: alerta, pede confirmação (sem fonte desde 11/10/2026)
        Map<String, Object> given = defaultForm(d);
        Map<String, Object> q4 = ((List<Map<String, Object>>) given.get("quarters")).get(3);
        q4.put("originalValue", new BigDecimal("53400"));
        q4.put("unit", "MILHAO");
        ResponseEntity<Map> r4 = post(user, BASE, given, Map.class);
        assertThat(r4.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codes(r4.getBody())).contains("PREMISSA_EXTREMA");

        given.put("confirmWarnings", true);
        ResponseEntity<Map> r5 = post(user, BASE, given, Map.class);
        assertThat(r5.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> saved = ((List<Map<String, Object>>) r5.getBody().get("quarters")).get(3);
        assertThat(saved.get("origin")).isEqualTo("INFORMADO");
        assertThat(number(saved.get("valueBrl"))).isEqualByComparingTo("53400000000");
        assertThat(saved).doesNotContainKey("sourceRef");
    }

    @Test
    @DisplayName("Lucro em dólar: PTAX do fim do trimestre por padrão; câmbio informado vale")
    @SuppressWarnings("unchecked")
    void earningsInDollars() {
        Map<String, Object> f = defaultForm(defaults("PETR4"));
        Map<String, Object> q4 = ((List<Map<String, Object>>) f.get("quarters")).get(3);
        q4.put("originalValue", new BigDecimal("5000"));
        q4.put("unit", "MILHAO");
        q4.put("currency", "USD");
        Map<String, Object> res = post(user, BASE + "/calculate", f, Map.class).getBody();
        // TTM = 32,7 + 15,5 + 35,2 bi + 5.000 mi × 5,4321
        assertThat(number(res.get("netIncomeTtm"))).isEqualByComparingTo("110560500000.00");

        q4.put("fxRate", new BigDecimal("5"));
        Map<String, Object> res2 = post(user, BASE + "/calculate", f, Map.class).getBody();
        assertThat(number(res2.get("netIncomeTtm"))).isEqualByComparingTo("108400000000.00");
    }

    @Test
    @DisplayName("Só ações; o cálculo de um usuário não aparece para outro")
    @SuppressWarnings("unchecked")
    void onlyStocksAndOwner() {
        assertThat(get(user, BASE + "/defaults/HGLG11", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        Map<String, Object> f = defaultForm(defaults("PETR4"));
        f.put("confirmWarnings", true);
        String id = (String) post(user, BASE, f, Map.class).getBody().get("id");
        User other = userWithPortfolio("outro-precoteto@activest.test");
        assertThat(get(other, BASE + "/" + id, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(other, BASE + "/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(other, BASE, List.class).getBody()).isEmpty();
        assertThat(delete(user, BASE + "/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Autowired
    private com.activest.domain.currency.ExchangeRateService exchangeRates;

    @Autowired
    CeilingPriceAlertService alerts;

    private void priceToday(String value) {
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker = 'PETR4') "
                + "AND reference_date = ?", java.sql.Date.valueOf(today));
        jdbc.update("""
                INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
                SELECT id, ?, CAST(? AS NUMERIC), 'B3_COTAHIST' FROM assets WHERE ticker = 'PETR4'""",
                java.sql.Date.valueOf(today), value);
    }

    private long priceAlerts() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'PRECO_ABAIXO_IDEAL'", Long.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object saveDefault() {
        Map<String, Object> f = defaultForm(defaults("PETR4"));
        f.put("confirmWarnings", true);
        ResponseEntity<Map> r = post(user, BASE, f, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().get("id");
    }

    @Test
    @DisplayName("Alerta de preço: desligado por padrão; ligado, avisa ao cruzar o ideal para baixo, rearma ao voltar e só um por dia")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void priceAlert() {
        Object id = saveDefault();
        List<Map<String, Object>> list = List.of(get(user, BASE, Map[].class).getBody());
        assertThat(list.get(0)).containsEntry("alertEnabled", false);

        // abaixo do ideal com o alerta desligado: nada
        priceToday("1.00");
        alerts.checkPrices(null, today);
        assertThat(priceAlerts()).isZero();

        org.springframework.http.HttpHeaders h = headers(user);
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<Map> lig = rest.exchange(BASE + "/" + id + "/alert", org.springframework.http.HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(Map.of("enabled", true), h), Map.class);
        assertThat(lig.getBody()).containsEntry("alertEnabled", true);

        // ligado com o preço já abaixo: avisa na próxima conferência; repetir não avisa de novo
        alerts.checkPrices(null, today);
        assertThat(priceAlerts()).isEqualTo(1);
        alerts.checkPrices(null, today);
        assertThat(priceAlerts()).isEqualTo(1);
        String note = jdbc.queryForObject("SELECT title FROM notifications WHERE kind = 'PRECO_ABAIXO_IDEAL'",
                String.class);
        assertThat(note).isEqualTo("PETR4 abaixo do Preço Ideal");

        // volta acima (rearma) e cai de novo no mesmo dia: no máximo um aviso por dia
        priceToday("999.00");
        alerts.checkPrices(null, today);
        priceToday("1.00");
        alerts.checkPrices(null, today);
        assertThat(priceAlerts()).isEqualTo(1);

        // no dia seguinte, a nova descida avisa
        priceToday("999.00");
        alerts.checkPrices(null, today);
        priceToday("1.00");
        alerts.checkPrices(null, today.plusDays(1));
        assertThat(priceAlerts()).isEqualTo(2);
    }

    @Test
    @DisplayName("Padrão mudou: balanço novo vira pendência; salvar o cálculo de novo resolve")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void defaultChanged() {
        Object id = saveDefault();
        alerts.checkDefaults(null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'PRECO_TETO_PADRAO_MUDOU'",
                Long.class)).isZero();

        long petr = jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, PETR);
        doc(petr, "ITR", "2026-09-30", "30000000000", "107000000000", "430000000000", "42000000000");
        alerts.checkDefaults(null);
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE kind = 'PRECO_TETO_PADRAO_MUDOU'",
                String.class)).isEqualTo("NAO_RESOLVIDA");

        Map<String, Object> f = defaultForm(defaults("PETR4"));
        f.put("confirmWarnings", true);
        assertThat(put(user, BASE + "/" + id, f, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE kind = 'PRECO_TETO_PADRAO_MUDOU'",
                String.class)).isEqualTo("RESOLVIDA");
    }
}

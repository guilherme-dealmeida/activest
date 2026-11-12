package com.activest.functional;

import com.activest.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X5 do docs/planos/PLANO_INDICES_MERCADO.md: leitura dos indices pela app-api — lista, serie e acumulado
 * (casos de conferencia da secao 6). A serie e gravada direto no banco, como a adm-api faria.
 */
class IndicesIT extends FunctionalBase {

    /** IPCA de 2024 (SGS 433): 4,83% no ano, o numero do IBGE. */
    private static final String[] IPCA_2024 = {"0.42", "0.83", "0.16", "0.38", "0.46", "0.21", "0.38", "-0.02",
            "0.44", "0.56", "0.39", "0.52"};

    private User user;

    private void insert(String code, LocalDate d, String v) {
        jdbc.update("INSERT INTO market_index_values (index_id, reference_date, value) "
                + "SELECT id, ?, ? FROM market_indices WHERE code = ?", Date.valueOf(d), new BigDecimal(v), code);
    }

    @BeforeEach
    void scenario() {
        user = userWithPortfolio("indices@activest.test");
        for (int m = 0; m < 12; m++) {
            insert("IPCA", LocalDate.of(2024, m + 1, 1), IPCA_2024[m]);
        }
        insert("CDI", LocalDate.of(2024, 1, 2), "1");
        insert("CDI", LocalDate.of(2024, 1, 3), "1");
        insert("CDI", LocalDate.of(2024, 1, 4), "1");
        insert("IBOV", LocalDate.of(2024, 1, 2), "132696.63");
        insert("IBOV", LocalDate.of(2024, 12, 30), "120283.40");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(String path) {
        ResponseEntity<Map> r = get(user, path, Map.class);
        assertThat(r.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @Test
    @DisplayName("Lista: os 19 indices (10 + 9 prazos da curva do Tesouro), com primeira e ultima data de quem tem serie")
    @SuppressWarnings("unchecked")
    void list() {
        List<Map<String, Object>> l = get(user, "/api/v1/indices", List.class).getBody();

        assertThat(l).hasSize(19);
        Map<String, Object> ipca = l.stream().filter(i -> "IPCA".equals(i.get("code"))).findFirst().orElseThrow();
        assertThat(ipca).containsEntry("firstDate", "2024-01-01").containsEntry("lastDate", "2024-12-01")
                .containsEntry("unit", "PCT_MES").containsEntry("accumulates", true);
        Map<String, Object> tr = l.stream().filter(i -> "TR".equals(i.get("code"))).findFirst().orElseThrow();
        assertThat(tr).containsEntry("accumulates", false).doesNotContainKey("lastDate");
    }

    @Test
    @DisplayName("Serie no periodo, em ordem")
    @SuppressWarnings("unchecked")
    void serie() {
        Map<String, Object> h = getMap("/api/v1/indices/ipca/history?from=2024-03-01&to=2024-05-31");

        List<Map<String, Object>> points = (List<Map<String, Object>>) h.get("points");
        assertThat(points).extracting(p -> p.get("date")).containsExactly("2024-03-01", "2024-04-01", "2024-05-01");
    }

    @Test
    @DisplayName("Acumulado: IPCA 2024 = 4,83%; meses inteiros mesmo com o dia no meio do mes")
    void accumulatedIpca() {
        Map<String, Object> a = getMap("/api/v1/indices/IPCA/accumulated?from=2024-01-15&to=2024-12-31");

        assertThat(a.get("periods")).isEqualTo(12);
        assertThat(new BigDecimal(a.get("percent").toString()).setScale(2, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("4.83");
    }

    @Test
    @DisplayName("Acumulado: 110% do CDI e CDI + spread; Ibovespa por nivel")
    void variants() {
        Map<String, Object> pct = getMap("/api/v1/indices/CDI/accumulated?from=2024-01-02&to=2024-01-03&percent=110");
        assertThat(new BigDecimal(pct.get("factor").toString())).isEqualByComparingTo("1.022121");
        assertThat(pct.get("periods")).isEqualTo(2);

        Map<String, Object> ibov = getMap("/api/v1/indices/IBOV/accumulated?from=2024-01-02&to=2024-12-31");
        assertThat(ibov.get("lastDate")).isEqualTo("2024-12-30");
        assertThat(new BigDecimal(ibov.get("percent").toString()).setScale(2, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("-9.35");
    }

    @Test
    @DisplayName("Grafico do card: IPCA com periodo especifico (meses inteiros), resumo e pontos; atalho invalido 422")
    @SuppressWarnings("unchecked")
    void chart() {
        Map<String, Object> c = getMap("/api/v1/indices/IPCA/chart?from=2024-01-15&to=2024-12-31");
        assertThat(c).containsEntry("range", "CUSTOM").containsEntry("from", "2024-01-01").containsEntry("to", "2024-12-01")
                .containsEntry("displayUnit", "% no mês").containsEntry("firstDate", "2024-01-01");
        assertThat((List<Object>) c.get("points")).hasSize(12);
        assertThat(new BigDecimal(c.get("accumulatedPct").toString()).setScale(2, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("4.83");

        Map<String, Object> ibov = getMap("/api/v1/indices/IBOV/chart?range=MAX");
        assertThat(new BigDecimal(ibov.get("changePct").toString())).isEqualByComparingTo("-9.35");

        Map<String, Object> cdi = getMap("/api/v1/indices/CDI/chart?range=1D");
        assertThat((List<Object>) cdi.get("points")).hasSize(1);
        assertThat(cdi.get("displayUnit")).isEqualTo("% a.a.");

        Map<String, Object> empty = getMap("/api/v1/indices/USD_PTAX/chart");
        assertThat((List<Object>) empty.get("points")).isEmpty();

        assertThat(get(user, "/api/v1/indices/IPCA/chart?range=3X", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // visao anual dos mensais: acumulado em 12 meses (so dez/2024 tem 12 meses de historia aqui)
        Map<String, Object> annual = getMap("/api/v1/indices/IPCA/chart?range=MAX&view=ANUAL");
        assertThat(annual).containsEntry("view", "ANUAL").containsEntry("displayUnit", "% a.a.");
        List<Map<String, Object>> twelve = (List<Map<String, Object>>) annual.get("points");
        assertThat(twelve).hasSize(1);
        assertThat(twelve.get(0).get("date")).isEqualTo("2024-12-01");
        assertThat(new BigDecimal(annual.get("last").toString()).setScale(2, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("4.83");
        assertThat(c).containsEntry("view", "MENSAL");
        assertThat(get(user, "/api/v1/indices/CDI/chart?view=ANUAL", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get(user, "/api/v1/indices/IPCA/chart?view=SEMANAL", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // indice mensal nao tem 1D nem 7D
        assertThat(get(user, "/api/v1/indices/IPCA/chart?range=1D", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get(user, "/api/v1/indices/IGPM/chart?range=7D", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(getMap("/api/v1/indices/IPCA/chart?range=1M").get("range")).isEqualTo("1M");
    }

    @Test
    @DisplayName("Erros: indice desconhecido 404; TR nao acumula 422; periodo sem valores 422; sem token 401")
    void errors() {
        assertThat(get(user, "/api/v1/indices/XPTO/history", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(user, "/api/v1/indices/TR/accumulated?from=2024-01-01", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get(user, "/api/v1/indices/SELIC/accumulated?from=2024-01-01", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get(user, "/api/v1/indices/IBOV/accumulated?from=2024-01-02&percent=110", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rest.exchange("/api/v1/indices", HttpMethod.GET, HttpEntity.EMPTY, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Curva do dia (PLANO_JUROS_FUTUROS, J3): prazos em ordem, ultimo dia com dado ate a data, prazo sem valor nulo; nao acumula")
    @SuppressWarnings("unchecked")
    void yieldCurve() {
        insert("JUROS_PRE_1A", LocalDate.of(2024, 1, 2), "11.5");
        insert("JUROS_PRE_5A", LocalDate.of(2024, 1, 2), "10.9");
        insert("JUROS_PRE_8A", LocalDate.of(2024, 1, 2), "11.2");
        insert("JUROS_PRE_1A", LocalDate.of(2024, 6, 3), "10.6");
        insert("JUROS_PRE_5A", LocalDate.of(2024, 6, 3), "11.8");

        Map<String, Object> c = getMap("/api/v1/indices/curves/pre?dates=2024-06-05,2024-01-02,2023-12-01");

        assertThat(c).containsEntry("curve", "PRE").containsEntry("name", "Juros prefixados (Tesouro)");
        List<Map<String, Object>> vs = (List<Map<String, Object>>) c.get("vertices");
        assertThat(vs).extracting(v -> v.get("years")).containsExactly(1, 2, 3, 5, 8);
        List<Map<String, Object>> ds = (List<Map<String, Object>>) c.get("dates");
        assertThat(ds.get(0)).containsEntry("requested", "2024-06-05").containsEntry("date", "2024-06-03");
        List<Object> june = (List<Object>) ds.get(0).get("rates");
        assertThat(june).hasSize(5);
        assertThat(new BigDecimal(june.get(0).toString())).isEqualByComparingTo("10.6");
        assertThat(june.get(1)).isNull();
        assertThat(june.get(4)).isNull(); // 8 anos sem valor em 03/06
        assertThat(ds.get(1)).containsEntry("date", "2024-01-02");
        assertThat(ds.get(2).get("date")).isNull();
        assertThat(((List<Object>) ds.get(2).get("rates"))).containsOnlyNulls();

        assertThat(((List<Object>) getMap("/api/v1/indices/curves/REAL").get("vertices"))).hasSize(4);
        assertThat(get(user, "/api/v1/indices/curves/XPTO", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get(user, "/api/v1/indices/curves/PRE?dates=2024-01-01,2024-01-02,2024-01-03,2024-01-04",
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get(user, "/api/v1/indices/JUROS_PRE_5A/accumulated?from=2024-01-01", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(getMap("/api/v1/indices/JUROS_PRE_5A/chart?from=2024-01-01&to=2024-12-31"))
                .containsEntry("displayUnit", "% a.a.");
    }
}

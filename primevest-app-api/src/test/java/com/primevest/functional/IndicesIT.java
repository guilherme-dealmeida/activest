package com.primevest.functional;

import com.primevest.domain.user.User;
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
class IndicesIT extends FuncionalBase {

    /** IPCA de 2024 (SGS 433): 4,83% no ano, o numero do IBGE. */
    private static final String[] IPCA_2024 = {"0.42", "0.83", "0.16", "0.38", "0.46", "0.21", "0.38", "-0.02",
            "0.44", "0.56", "0.39", "0.52"};

    private User user;

    private void insert(String code, LocalDate d, String v) {
        jdbc.update("INSERT INTO market_index_values (index_id, reference_date, value) "
                + "SELECT id, ?, ? FROM market_indices WHERE code = ?", Date.valueOf(d), new BigDecimal(v), code);
    }

    @BeforeEach
    void cenario() {
        user = usuarioComCarteira("indices@primevest.test");
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
    @DisplayName("Lista: os 10 indices, com primeira e ultima data de quem tem serie")
    @SuppressWarnings("unchecked")
    void lista() {
        List<Map<String, Object>> l = get(user, "/api/v1/indices", List.class).getBody();

        assertThat(l).hasSize(10);
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
    void ipcaAcumulado() {
        Map<String, Object> a = getMap("/api/v1/indices/IPCA/accumulated?from=2024-01-15&to=2024-12-31");

        assertThat(a.get("periods")).isEqualTo(12);
        assertThat(new BigDecimal(a.get("percent").toString()).setScale(2, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("4.83");
    }

    @Test
    @DisplayName("Acumulado: 110% do CDI e CDI + spread; Ibovespa por nivel")
    void variantes() {
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
    void grafico() {
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

        Map<String, Object> vazio = getMap("/api/v1/indices/USD_PTAX/chart");
        assertThat((List<Object>) vazio.get("points")).isEmpty();

        assertThat(get(user, "/api/v1/indices/IPCA/chart?range=3X", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // visao anual dos mensais: acumulado em 12 meses (so dez/2024 tem 12 meses de historia aqui)
        Map<String, Object> anual = getMap("/api/v1/indices/IPCA/chart?range=MAX&view=ANUAL");
        assertThat(anual).containsEntry("view", "ANUAL").containsEntry("displayUnit", "% a.a.");
        List<Map<String, Object>> doze = (List<Map<String, Object>>) anual.get("points");
        assertThat(doze).hasSize(1);
        assertThat(doze.get(0).get("date")).isEqualTo("2024-12-01");
        assertThat(new BigDecimal(anual.get("last").toString()).setScale(2, java.math.RoundingMode.HALF_UP))
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
    void erros() {
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
}

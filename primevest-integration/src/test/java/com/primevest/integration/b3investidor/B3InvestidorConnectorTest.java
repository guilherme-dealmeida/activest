package com.primevest.integration.b3investidor;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.asset.PriceSource;
import com.primevest.domain.integration.credential.ApiKeyCredentials;
import com.primevest.domain.integration.credential.InvestorDocument;
import com.primevest.domain.integration.source.CredentialCheck;
import com.primevest.domain.integration.source.FetchResult;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.integration.source.MovementSide;
import com.primevest.support.ContaSinteticaB3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.primevest.support.ContaSinteticaB3.PATHS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** O conector da B3 contra a conta inventada ({@link ContaSinteticaB3}) — sem rede, sem dado real. */
class B3InvestidorConnectorTest {

    private static final InvestorDocument CPF = new InvestorDocument(ContaSinteticaB3.CPF);

    private B3InvestidorClient client;
    private B3InvestidorConnector connector;

    @BeforeEach
    void setUp() {
        client = mock(B3InvestidorClient.class);
        B3InvestidorProperties props = new B3InvestidorProperties();
        props.setEnabled(true);
        props.setOptinUrl("http://localhost:8090/optin");
        connector = new B3InvestidorConnector(client, props);
        agora("2026-10-03T12:00:00-03:00");
    }

    private void agora(String instante) {
        connector.clock = Clock.fixed(ZonedDateTime.parse(instante).toInstant(), B3InvestidorConnector.SAO_PAULO);
    }

    private static Map<String, Object> periodo(String inicio, String fim) {
        return Map.of("referenceStartDate", inicio, "referenceEndDate", fim);
    }

    // ------------------------------------------------------------------ autorizacao

    @Test
    @DisplayName("CPF autorizado: aceito, so leitura e utilizavel")
    void autorizado() {
        ContaSinteticaB3.conta(client, true);

        CredentialCheck c = connector.check(CPF);

        assertThat(c.usable()).isTrue();
        assertThat(c.permissions()).contains("\"authorized\":true");
    }

    @Test
    @DisplayName("CPF que ainda nao deu o opt-in: aguardando autorizacao (G8), com o motivo")
    void aguardandoAutorizacao() {
        ContaSinteticaB3.conta(client, false);

        CredentialCheck c = connector.check(CPF);

        assertThat(c.accepted()).isTrue();
        assertThat(c.awaitingAuthorization()).isTrue();
        assertThat(c.usable()).isFalse();
        assertThat(c.problem()).contains("autorizar o PrimeVest");
        assertThat(connector.authorizationUrl()).isEqualTo("http://localhost:8090/optin");
    }

    @Test
    @DisplayName("CPF que a B3 nao localiza (422.03): recusado")
    void cpfDesconhecido() {
        ContaSinteticaB3.conta(client, true);

        CredentialCheck c = connector.check(new InvestorDocument(ContaSinteticaB3.CPF_DESCONHECIDO));

        assertThat(c.accepted()).isFalse();
        assertThat(c.problem()).contains("não localizou");
    }

    @Test
    @DisplayName("Credencial de outra fonte e erro claro")
    void credencialErrada() {
        assertThatThrownBy(() -> connector.check(new ApiKeyCredentials("chave-ficticia-AB12", "segredo-ficticio")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("InvestorDocument");
    }

    // ------------------------------------------------------------------ busca

    @Test
    @DisplayName("Primeira busca: do inicio do historico da B3 (A5) ate D-1; cursores na ultima data")
    void primeiraBusca() {
        ContaSinteticaB3.conta(client, true);

        FetchResult r = connector.fetch(CPF, Map.of());

        verify(client).getAll(eq(PATHS.getAssetTrading()), anyMap(), eq(periodo("2019-11-01", "2026-10-02")));
        verify(client).getAll(eq(PATHS.getMovement()), argThat(v -> "equities".equals(v.get("product"))),
            eq(periodo("2019-11-01", "2026-10-02")));
        assertThat(r.cursors()).containsEntry(B3InvestidorConnector.NEGOCIACAO, "2026-10-02")
            .containsEntry(B3InvestidorConnector.MOVIMENTACAO, "2026-10-02");
        assertThat(r.balances()).containsEntry("PETR4", new BigDecimal("77")).containsEntry("HGLG11", new BigDecimal("10"));

        Map<MovementKind, Long> porTipo = r.movements().stream()
            .collect(Collectors.groupingBy(ImportedMovement::kind, Collectors.counting()));
        assertThat(porTipo).containsEntry(MovementKind.EQUITY_TRADE, (long) ContaSinteticaB3.GRUPOS)
            .containsEntry(MovementKind.SETTLEMENT, 3L)
            .containsEntry(MovementKind.CASH_EVENT, 3L)
            .containsEntry(MovementKind.STOCK_EVENT, 1L)
            .containsEntry(MovementKind.RIGHTS, 1L)
            .containsEntry(MovementKind.OTHER, 1L)
            .containsEntry(MovementKind.FIXED_INCOME, 1L);
        assertThat(r.movements()).extracting(ImportedMovement::externalId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("B1: negocios do mesmo dia, papel (fracionario junto) e lado viram um movimento, mesmo em paginas diferentes")
    void agrupamento() {
        ContaSinteticaB3.conta(client, true);

        ImportedMovement petr4 = connector.fetch(CPF, Map.of()).movements().stream()
            .filter(m -> m.externalId().equals("b3:negociacao:2024-03-04:PETR4:C")).findFirst().orElseThrow();

        assertThat(petr4.kind()).isEqualTo(MovementKind.EQUITY_TRADE);
        assertThat(petr4.asset()).isEqualTo("PETR4");
        assertThat(petr4.side()).isEqualTo(MovementSide.BUY);
        assertThat(petr4.grossQuantity()).isEqualByComparingTo("107");
        assertThat(petr4.quoteAmount()).isEqualByComparingTo("4119.64");
        assertThat(petr4.quoteAsset()).isEqualTo("BRL");
        assertThat(petr4.tradeDate()).isEqualTo(LocalDate.of(2024, 3, 4));
        assertThat(petr4.market()).isEqualTo("Mercado à Vista");
        assertThat(petr4.participant()).isEqualTo("CORRETORA FICTICIA S.A.");
        assertThat(petr4.raw()).contains("PETR4F");
    }

    @Test
    @DisplayName("Com as regras e o catalogo: as 3 negociacoes de renda variavel e a compra de Tesouro (F7) viram transacao")
    void comAsRegras() {
        ContaSinteticaB3.conta(client, true);
        AssetType acao = AssetType.builder().id(1L).name("ACAO").priceSource(PriceSource.BRAPI).build();
        AssetType fii = AssetType.builder().id(2L).name("FII").priceSource(PriceSource.BRAPI).build();
        AssetType tesouro = AssetType.builder().id(4L).name("TESOURO_DIRETO").priceSource(PriceSource.MANUAL).build();
        Map<String, Asset> catalogo = Map.of(
            "PETR4", Asset.builder().id(20L).ticker("PETR4").assetType(acao).build(),
            "HGLG11", Asset.builder().id(21L).ticker("HGLG11").assetType(fii).build(),
            "Tesouro Selic 2029", Asset.builder().id(30L).ticker("Tesouro Selic 2029").assetType(tesouro).build());

        long novas = connector.fetch(CPF, Map.of()).movements().stream()
            .filter(m -> connector.rules().apply(m, t -> Optional.ofNullable(catalogo.get(t))).mapped())
            .count();

        assertThat(novas).isEqualTo(ContaSinteticaB3.NOVAS);
    }

    @Test
    @DisplayName("A6: antes das 8h de Brasilia o dia anterior ainda nao saiu — a busca vai ate D-2")
    void antesDasOito() {
        ContaSinteticaB3.conta(client, true);
        agora("2026-10-03T07:59:00-03:00");

        assertThat(connector.fetch(CPF, Map.of()).cursors()).containsEntry(B3InvestidorConnector.NEGOCIACAO, "2026-10-01");
    }

    @Test
    @DisplayName("Cursor ja no ultimo dia publicado: nao pede negociacao nem movimentacao de novo")
    void cursorEmDia() {
        ContaSinteticaB3.conta(client, true);
        Map<String, String> cursores = Map.of(B3InvestidorConnector.NEGOCIACAO, "2026-10-02",
            B3InvestidorConnector.MOVIMENTACAO, "2026-10-02");

        FetchResult r = connector.fetch(CPF, cursores);

        verify(client, never()).getAll(eq(PATHS.getAssetTrading()), anyMap(), anyMap());
        verify(client, never()).getAll(eq(PATHS.getMovement()), anyMap(), anyMap());
        assertThat(r.cursors()).isEqualTo(cursores);
        // So a foto do dia: proventos a receber.
        assertThat(r.movements()).extracting(ImportedMovement::kind).containsOnly(MovementKind.CASH_EVENT);
    }

    @Test
    @DisplayName("Cursor de ontem: pede so o dia seguinte")
    void cursorDeOntem() {
        ContaSinteticaB3.conta(client, true);

        connector.fetch(CPF, Map.of(B3InvestidorConnector.NEGOCIACAO, "2026-09-30",
            B3InvestidorConnector.MOVIMENTACAO, "2026-09-30"));

        verify(client).getAll(eq(PATHS.getAssetTrading()), anyMap(), eq(periodo("2026-10-01", "2026-10-02")));
    }

    @Test
    @DisplayName("Investidor revogou (422.02 na busca): o erro sobe")
    void revogadoNaBusca() {
        ContaSinteticaB3.conta(client, false);

        assertThatThrownBy(() -> connector.fetch(CPF, Map.of()))
            .isInstanceOf(B3InvestidorException.class)
            .satisfies(e -> assertThat(((B3InvestidorException) e).code()).isEqualTo("422.02"));
    }

    @Test
    @DisplayName("Rota de conferencia em branco nao e consultada")
    void rotaEmBranco() {
        ContaSinteticaB3.conta(client, true);
        B3InvestidorProperties props = new B3InvestidorProperties();
        props.getPaths().setPosition("");
        props.getPaths().setProvisionedEvents(" ");
        B3InvestidorConnector semConferencia = new B3InvestidorConnector(client, props);
        semConferencia.clock = connector.clock;

        FetchResult r = semConferencia.fetch(CPF, Map.of());

        assertThat(r.balances()).isEmpty();
        assertThat(r.movements()).noneMatch(m -> m.externalId().startsWith("b3:provisionado:"));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
        "Transferência - Liquidação,SETTLEMENT",
        "Dividendo,CASH_EVENT",
        "Juros Sobre Capital Próprio,CASH_EVENT",
        "Rendimento,CASH_EVENT",
        "Amortização,CASH_EVENT",
        "Atualização,STOCK_EVENT",
        "Bonificação em Ativos,STOCK_EVENT",
        "Desdobro,STOCK_EVENT",
        "Leilão de Fração,STOCK_EVENT",
        "Direito de Subscrição,RIGHTS",
        "Direitos de Subscrição - Não Exercido,RIGHTS",
        "Cessão de Direitos - Solicitada,RIGHTS",
        "Empréstimo,LENDING",
        "Transferência,CUSTODY_TRANSFER",
        "Resgate,OTHER"
    })
    @DisplayName("Tipo da Movimentacao da B3 -> MovementKind (6.4)")
    void classificacao(String tipo, MovementKind esperado) {
        assertThat(B3InvestidorConnector.classificar(tipo)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Numero da B3: JSON, ou texto com virgula decimal; '-' e vazio sao nulos (A4)")
    void numeros() {
        assertThat(B3InvestidorConnector.dec(ContaSinteticaB3.json("1479"))).isEqualByComparingTo("1479");
        assertThat(B3InvestidorConnector.dec(ContaSinteticaB3.json("\"0,25\""))).isEqualByComparingTo("0.25");
        assertThat(B3InvestidorConnector.dec(ContaSinteticaB3.json("\"1.479,25\""))).isEqualByComparingTo("1479.25");
        assertThat(B3InvestidorConnector.dec(ContaSinteticaB3.json("\"-\""))).isNull();
        assertThat(B3InvestidorConnector.dec(ContaSinteticaB3.json("null"))).isNull();
        assertThat(B3InvestidorConnector.lado("Débito")).isEqualTo(MovementSide.SELL);
        assertThat(B3InvestidorConnector.lado("Credito")).isEqualTo(MovementSide.BUY);
        assertThat(B3InvestidorConnector.lado("Venda")).isEqualTo(MovementSide.SELL);
    }
}

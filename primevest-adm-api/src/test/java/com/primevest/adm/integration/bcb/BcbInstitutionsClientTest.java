package com.primevest.adm.integration.bcb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Leitura do cadastro do IF.data e das sedes (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, 4.1). */
class BcbInstitutionsClientTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("Cadastro: instituicoes individuais pela raiz do CNPJ; linhas C0 viram conglomerados")
    void signupData() {
        BcbInstitutionsClient.Registry r = BcbFixtures.registry();

        assertThat(r.baseMonth()).isEqualTo("202606");
        assertThat(r.institutions()).hasSize(10);
        assertThat(r.groups()).containsOnlyKeys("C0084693", "C0082475", "C0080745", "C0084686");
        assertThat(r.groups().get("C0084693").name()).isEqualTo("NU PAGAMENTOS - PRUDENCIAL");
        assertThat(r.groups().get("C0084693").leaderRoot()).isEqualTo("18236120");
        BcbInstitution nuFinance = r.institutions().stream()
                .filter(i -> i.cnpjRoot().equals("30680829")).findFirst().orElseThrow();
        assertThat(nuFinance.segment()).isEqualTo("Sociedade de Crédito, Financiamento e Investimento");
        assertThat(nuFinance.groupCode()).isEqualTo("C0084693");
        assertThat(nuFinance.situation()).isEqualTo("A");
    }

    @Test
    @DisplayName("Sites das instituicoes vivas, em minusculas, pela raiz")
    void sites() {
        assertThat(BcbFixtures.websites()).containsEntry("30680829", "www.nubank.com.br")
                .containsEntry("62169875", "https://www.nuinvest.com.br/");
    }

    @Test
    @DisplayName("Raiz do CNPJ: de raiz curta, de CNPJ formatado ou de 14 digitos; codigo de conglomerado nao e CNPJ")
    void root() {
        assertThat(BcbInstitutionsClient.root8("360305")).isEqualTo("00360305");
        assertThat(BcbInstitutionsClient.root8("33.264.668/0001-03")).isEqualTo("33264668");
        assertThat(BcbInstitutionsClient.root8("00000000000191")).isEqualTo("00000000");
        assertThat(BcbInstitutionsClient.root8("C0084693")).isNull();
        assertThat(BcbInstitutionsClient.root8("")).isNull();
    }

    @Test
    @DisplayName("Data-base mais recente: a nao publicada (ou fonte fora do ar) e pulada e vale a anterior em disco")
    void latestBaseDate() throws Exception {
        Path ifdata = Files.createDirectories(tmp.resolve("ifdata"));
        Files.writeString(ifdata.resolve("cadastro_202606.json"), BcbFixtures.read("ifdata_cadastro_202606.json"));
        BcbInstitutionsClient client = new BcbInstitutionsClient("http://127.0.0.1:9", "http://127.0.0.1:9", tmp,
                Duration.ofSeconds(2), Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

        BcbInstitutionsClient.Registry r = client.latestRegistry();

        assertThat(r.baseMonth()).isEqualTo("202606");
        assertThat(r.institutions()).hasSize(10);
    }

    @Test
    @DisplayName("Sem nenhuma data-base em disco e com a fonte fora do ar, a falha sobe")
    void withoutSource() {
        BcbInstitutionsClient client = new BcbInstitutionsClient("http://127.0.0.1:9", "http://127.0.0.1:9", tmp,
                Duration.ofSeconds(1), Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

        assertThatThrownBy(client::latestRegistry).isInstanceOf(BcbException.class);
    }

    @Test
    @DisplayName("Resposta vazia (data-base ainda nao publicada) nao fica no cache")
    void empty() {
        assertThat(BcbInstitutionsClient.parseRegistry("202609", "{\"value\":[]}").institutions()).isEmpty();
    }
}

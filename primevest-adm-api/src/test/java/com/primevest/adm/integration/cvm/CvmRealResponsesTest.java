package com.primevest.adm.integration.cvm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.domain.issuer.ShareClass;
import com.primevest.adm.integration.b3.B3CompanyDetail;
import com.primevest.adm.integration.b3.B3Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O formato dos arquivos reais da CVM (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 1.1 e 1.2).
 * Se a CVM mudar coluna ou charset, o teste quebra antes de gravar dado errado.
 */
class CvmRealResponsesTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("Cadastro: uma entrada por CNPJ, marcando quem e ATIVO em BOLSA")
    void registry() throws Exception {
        Map<String, CvmCompany> byCnpj;
        try (InputStream in = Files.newInputStream(CvmFixtures.resource("cad_cia_aberta.csv"))) {
            byCnpj = CvmCompany.parse(in);
        }

        // Petrobras aparece em BOLSA e em BALCAO ORGANIZADO: vira uma so
        CvmCompany petr = byCnpj.get("33000167000101");
        assertThat(petr.cvmCode()).isEqualTo(9512);
        assertThat(petr.tradeName()).isEqualTo("PETROBRAS");
        assertThat(petr.legalName()).isEqualTo("PETRÓLEO BRASILEIRO S.A. - PETROBRAS");
        assertThat(petr.activeOnExchange()).isTrue();
        assertThat(petr.sector()).isEqualTo("Petróleo e Gás");

        // Itau tem um registro antigo cancelado (1279) e o ativo (19348): vale o ativo
        CvmCompany itau = byCnpj.get("60872504000123");
        assertThat(itau.cvmCode()).isEqualTo(19348);
        assertThat(itau.activeOnExchange()).isTrue();

        // Ativa so em balcao nao entra no criterio D2; cancelada tambem nao
        assertThat(byCnpj.get("45456117000112").activeOnExchange()).isFalse();
        CvmCompany villares = byCnpj.get("60664810000174");
        assertThat(villares.status()).isEqualTo("CANCELADA");
        assertThat(villares.activeOnExchange()).isFalse();
    }

    @Test
    @DisplayName("FCA: fim do exercicio e valores mobiliarios, com a composicao da unit em texto livre")
    void fca() {
        Path zip = CvmFixtures.zip("fca_cia_aberta_2026", 2026, tmp);

        CvmFca.General petr = CvmFca.general(zip, 2026).get("33000167000101");
        assertThat(petr.cvmCode()).isEqualTo(9512);
        assertThat(petr.fiscalYearEndMonth()).isEqualTo((short) 12);

        Map<String, List<CvmFca.Security>> secs = CvmFca.securities(zip, 2026);
        assertThat(secs.get("33000167000101")).filteredOn(CvmFca.Security::onExchange)
                .extracting(CvmFca.Security::ticker).containsExactlyInAnyOrder("PETR3", "PETR4");
        // Klabin: o FCA so traz a unit; KLBN3 e KLBN4 ficam para a raiz do ticker (F3)
        assertThat(secs.get("89637490000145")).filteredOn(s -> "KLBN11".equals(s.ticker()))
                .singleElement().extracting(CvmFca.Security::unitComposition).isEqualTo("1 KLBN3 + 4 KLBN4");
        assertThat(secs.get("07859971000130")).filteredOn(s -> "TAEE11".equals(s.ticker()))
                .singleElement().extracting(CvmFca.Security::unitComposition).isEqualTo("1 ON / 2 PN");
    }

    @Test
    @DisplayName("Informe de FII: fica o mes mais recente; segmento da CVM e fraco")
    void fiiReport() {
        Path zip = CvmFixtures.zip("inf_mensal_fii_2026", 2026, tmp);

        Map<String, CvmFiiGeneral> latest = CvmFiiGeneral.latest(zip, 2026);
        CvmFiiGeneral hglg = latest.get("11728688000147");
        assertThat(hglg.referenceDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(hglg.onExchange()).isTrue();
        assertThat(hglg.name()).startsWith("PÁTRIA LOG");
        assertThat(hglg.isin()).isEqualTo("BRHGLGCTF004");
        // HGLG11 e logistica, mas a CVM diz "Multicategoria": por isso o segmento curado vem do seed
        assertThat(hglg.cvmSegment()).isEqualTo("Multicategoria");
        assertThat(latest.get("00613094000174").onExchange()).isFalse();
    }

    @Test
    @DisplayName("CSV: aspas, ponto e virgula dentro do campo e CNPJ so com digitos")
    void csv() {
        assertThat(CvmCsv.split("a;\"b;c\";\"d \"\"e\"\"\";")).containsExactly("a", "b;c", "d \"e\"", "");
        assertThat(CvmCsv.cnpj("33.000.167/0001-01")).isEqualTo("33000167000101");
        assertThat(CvmCsv.cnpj("613094000174")).isEqualTo("00613094000174");
        assertThat(CvmCsv.cnpj("")).isNull();
    }

    @Test
    @DisplayName("B3: classificacao setorial com a virgula que a B3 devolve como ponto")
    void b3Classification() throws Exception {
        B3CompanyDetail petr = new ObjectMapper().readValue(B3Fixtures.read("detail_PETR.json"), B3CompanyDetail.class);
        assertThat(petr.classification()).containsExactly(
                "Petróleo, Gás e Biocombustíveis", "Petróleo, Gás e Biocombustíveis", "Exploração, Refino e Distribuição");
        // abreviatura nao vira virgula
        assertThat(new B3CompanyDetail(null, null, null, null,
                "Bens Industriais / Máquinas e Equipamentos / Máq. e Equip. Construção e Agrícolas", null)
                .classification().get(2)).isEqualTo("Máq. e Equip. Construção e Agrícolas");
        // visto na carga real de 27/09: ponto solto entre espacos
        assertThat(new B3CompanyDetail(null, null, null, null,
                "Bens Industriais / Máquinas e Equipamentos / Motores . Compressores e Outros", null)
                .classification().get(2)).isEqualTo("Motores, Compressores e Outros");
        assertThat(new B3CompanyDetail(null, null, null, null,
                "Saúde / Serv.Méd.Hospit..Análises e Diagnósticos / Serv.Méd.Hospit..Análises e Diagnósticos", null)
                .classification().get(2)).isEqualTo("Serv.Méd.Hospit.,Análises e Diagnósticos");
    }

    @Test
    @DisplayName("Classe da acao pelo numero do ticker, ignorando o F do fracionario")
    void classByTicker() {
        assertThat(ShareClass.fromTicker("PETR3")).isEqualTo(ShareClass.ON);
        assertThat(ShareClass.fromTicker("PETR4F")).isEqualTo(ShareClass.PN);
        assertThat(ShareClass.fromTicker("KLBN11")).isEqualTo(ShareClass.UNIT);
        assertThat(ShareClass.fromTicker("USIM5")).isEqualTo(ShareClass.PNA);
        assertThat(ShareClass.fromTicker("XPTO9")).isNull();
    }
}

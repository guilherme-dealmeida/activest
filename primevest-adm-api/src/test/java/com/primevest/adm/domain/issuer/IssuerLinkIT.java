package com.primevest.adm.domain.issuer;

import com.primevest.domain.issuer.Issuer;
import com.primevest.domain.issuer.IssuerKind;
import com.primevest.domain.issuer.IssuerLinkSource;
import com.primevest.domain.issuer.IssuerRepository;
import com.primevest.domain.issuer.ShareClass;

import com.primevest.adm.integration.b3.B3Client;
import com.primevest.adm.integration.b3.B3Exception;
import com.primevest.adm.integration.b3.B3Supplement;
import com.primevest.adm.integration.cvm.CvmDataset;
import com.primevest.adm.integration.cvm.CvmFixtures;
import com.primevest.adm.integration.cvm.CvmOpenDataClient;
import com.primevest.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * F3 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: ligacao ativo -> emissor com os recortes
 * reais da CVM (Petrobras, Itau, Klabin, Taesa; HGLG11 e PABY11).
 */
class IssuerLinkIT extends IntegrationTestBase {

    @TempDir
    static Path tmp;

    @MockBean
    CvmOpenDataClient cvm;

    @MockBean
    B3Client b3;

    @Autowired
    IssuerImportService importer;

    @Autowired
    IssuerLinkService links;

    @Autowired
    IssuerRepository issuers;

    @BeforeEach
    void cenario() {
        jdbc.update("DELETE FROM unit_compositions");
        jdbc.update("UPDATE assets SET issuer_id = NULL, share_class = NULL, issuer_link_source = NULL");
        jdbc.update("DELETE FROM issuers");

        int year = LocalDate.now(ZoneId.of("America/Sao_Paulo")).getYear();
        when(cvm.companyRegistry()).thenReturn(CvmFixtures.companyRegistry(tmp));
        when(cvm.yearly(any(), anyInt())).thenReturn(Optional.empty());
        when(cvm.yearly(CvmDataset.FCA, year)).thenReturn(Optional.of(CvmFixtures.zip("fca_cia_aberta_2026", year, tmp)));
        when(cvm.yearly(CvmDataset.FII_MONTHLY, year))
                .thenReturn(Optional.of(CvmFixtures.zip("inf_mensal_fii_2026", year, tmp)));
        doThrow(new B3Exception("B3 respondeu vazio")).when(b3).companyDetail(anyString());
        doThrow(new B3Exception("B3 respondeu vazio")).when(b3).companySupplement(anyString());
        importer.importAll("teste");

        // Banco do Brasil fora dos recortes da CVM: so a B3 resolve a raiz BBAS
        issuers.save(Issuer.builder().kind(IssuerKind.COMPANY).cnpj("00000000000191").cvmCode(1023)
                .legalName("BCO BRASIL S.A.").registrationStatus("ATIVO").build());
        doReturn(new B3Supplement("BANCO DO BRASIL", "BBAS", "1023", List.of(), List.of()))
                .when(b3).companySupplement("BBAS");

        // o ISIN vem da serie historica da B3 (F2)
        jdbc.update("UPDATE assets SET isin = 'BRHGLGCTF004' WHERE ticker = 'HGLG11'");
        jdbc.update("UPDATE assets SET isin = 'BRPABYCTF012' WHERE ticker = 'PABY11'");
    }

    private Map<String, Object> ligacao(String ticker) {
        return jdbc.queryForMap("""
            SELECT a.share_class, a.issuer_link_source, i.cnpj, i.legal_name FROM assets a
            LEFT JOIN issuers i ON i.id = a.issuer_id WHERE a.ticker = ?""", ticker);
    }

    @Test
    @DisplayName("Acoes: FCA, raiz do ticker e B3; o fracionario segue o lote padrao; unit com composicao")
    void acoes() {
        IssuerLinkService.Result r = links.linkAll("teste");

        assertThat(ligacao("PETR4")).containsEntry("cnpj", "33000167000101").containsEntry("share_class", "PN")
                .containsEntry("issuer_link_source", "FCA");
        assertThat(ligacao("PETR3")).containsEntry("share_class", "ON").containsEntry("issuer_link_source", "FCA");
        assertThat(ligacao("PETR4F")).containsEntry("cnpj", "33000167000101").containsEntry("share_class", "PN");
        // Klabin: o FCA so traz a unit; KLBN3 e KLBN4 vem pela raiz
        assertThat(ligacao("KLBN11")).containsEntry("share_class", "UNIT").containsEntry("issuer_link_source", "FCA");
        assertThat(ligacao("KLBN4")).containsEntry("cnpj", "89637490000145").containsEntry("share_class", "PN")
                .containsEntry("issuer_link_source", "PREFIXO");
        assertThat(ligacao("BBAS3")).containsEntry("cnpj", "00000000000191").containsEntry("issuer_link_source", "B3");

        assertThat(jdbc.queryForList("""
            SELECT u.share_class, u.quantity FROM unit_compositions u JOIN assets a ON a.id = u.asset_id
            WHERE a.ticker = 'TAEE11' ORDER BY u.share_class""")).containsExactly(
                Map.of("share_class", "ON", "quantity", 1), Map.of("share_class", "PN", "quantity", 2));
        assertThat(jdbc.queryForObject("""
            SELECT u.quantity FROM unit_compositions u JOIN assets a ON a.id = u.asset_id
            WHERE a.ticker = 'KLBN11F' AND u.share_class = 'PN'""", Integer.class)).isEqualTo(4);
        assertThat(r.stocksBySource()).containsKeys(IssuerLinkSource.FCA, IssuerLinkSource.PREFIXO, IssuerLinkSource.B3);
    }

    @Test
    @DisplayName("FIIs pelo ISIN, inclusive o que a CVM marca como fora de bolsa (cria o emissor)")
    void fiis() {
        IssuerLinkService.Result r = links.linkAll("teste");

        assertThat(ligacao("HGLG11")).containsEntry("cnpj", "11728688000147").containsEntry("issuer_link_source", "ISIN");
        assertThat(ligacao("PABY11")).containsEntry("cnpj", "00613094000174").containsEntry("issuer_link_source", "ISIN");
        assertThat(r.fundsCreated()).isEqualTo(1);
        assertThat(issuers.findByCnpj("00613094000174").orElseThrow().getRegistrationStatus()).isEqualTo("FORA_DE_BOLSA");
    }

    @Test
    @DisplayName("Lista curada: confirma, cria emissor sem informe, acusa ticker de fora; MANUAL nao e sobrescrito")
    void listaCurada() {
        links.linkAll("teste");

        List<IssuerLinkService.CuratedLine> out = links.applyCuratedFunds(List.of(
                new IssuerLinkService.FundCnpj("HGLG11", "11728688000147"),
                new IssuerLinkService.FundCnpj("VGRH11", "67.624.043/0001-19"),
                new IssuerLinkService.FundCnpj("PETR4", "33000167000101"),
                new IssuerLinkService.FundCnpj("MXRF11", "abc")));

        assertThat(out).extracting(IssuerLinkService.CuratedLine::status).containsExactly(
                "CONFIRMADO", "EMISSOR_CRIADO_SEM_INFORME", "TICKER_FORA_DO_CATALOGO", "CNPJ_INVALIDO");
        assertThat(ligacao("VGRH11")).containsEntry("cnpj", "67624043000119").containsEntry("issuer_link_source", "MANUAL");
        assertThat(ligacao("HGLG11")).containsEntry("issuer_link_source", "MANUAL");

        // rodar a ligacao automatica de novo nao mexe no que e MANUAL
        links.linkAll("teste");
        assertThat(ligacao("HGLG11")).containsEntry("issuer_link_source", "MANUAL");
        assertThat(ligacao("VGRH11")).containsEntry("cnpj", "67624043000119");
    }

    @Test
    @DisplayName("B3 aponta codigo CVM que nao e emissor: cria a companhia pelo cadastro da CVM (caso WDCN3)")
    void b3CriaCompanhiaDoCadastro() {
        // Aguas do Sertao (CVM 27359) esta no cadastro, mas fora de bolsa: nao entra no import
        assertThat(issuers.findByCvmCode(27359)).isEmpty();
        doReturn(new B3Supplement("AGUAS DO SERTAO", "WDCN", "27359", List.of(), List.of()))
                .when(b3).companySupplement("WDCN");

        links.linkAll("teste");

        assertThat(ligacao("WDCN3")).containsEntry("cnpj", "45456117000112").containsEntry("share_class", "ON")
                .containsEntry("issuer_link_source", "B3");
        assertThat(ligacao("WDCN3F")).containsEntry("cnpj", "45456117000112");
        assertThat(issuers.findByCvmCode(27359).orElseThrow().getKind()).isEqualTo(IssuerKind.COMPANY);
    }

    private Long assetId(String ticker) {
        return jdbc.queryForObject("SELECT id FROM assets WHERE ticker = ?", Long.class, ticker);
    }

    @Test
    @DisplayName("Curadoria manual: bonus sem classe perde a composicao; unit com composicao manual; CNPJ do cadastro")
    void curadoriaManual() {
        links.linkAll("teste");
        Issuer petrobras = issuers.findByCnpj("33000167000101").orElseThrow();

        // ONCO11 como unit com composicao -> bonus, sem classe
        links.setManual(assetId("ONCO11"), petrobras.getId(), null, ShareClass.UNIT);
        links.setUnitComposition(assetId("ONCO11"), Map.of(ShareClass.ON, 1));
        links.setManual(assetId("ONCO11"), petrobras.getId(), null, null);
        assertThat(ligacao("ONCO11")).containsEntry("share_class", null).containsEntry("issuer_link_source", "MANUAL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_compositions WHERE asset_id = ?", Integer.class,
                assetId("ONCO11"))).isZero();

        // BPAC11: 1 ON + 2 PNA
        links.setManual(assetId("BPAC11"), petrobras.getId(), null, ShareClass.UNIT);
        links.setUnitComposition(assetId("BPAC11"), Map.of(ShareClass.ON, 1, ShareClass.PNA, 2));
        assertThat(jdbc.queryForList("""
            SELECT share_class, quantity FROM unit_compositions WHERE asset_id = ? ORDER BY share_class""",
                assetId("BPAC11"))).containsExactly(
                Map.of("share_class", "ON", "quantity", 1), Map.of("share_class", "PNA", "quantity", 2));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> links.setUnitComposition(assetId("PETR4"), Map.of(ShareClass.ON, 1)))
                .isInstanceOf(IllegalStateException.class);

        // CNPJ que so esta no cadastro da CVM
        links.setManual(assetId("WDCN3"), null, "45.456.117/0001-12", ShareClass.ON);
        assertThat(ligacao("WDCN3")).containsEntry("cnpj", "45456117000112").containsEntry("issuer_link_source", "MANUAL");

        // a rotina automatica preserva tudo o que e MANUAL
        links.linkAll("teste");
        assertThat(ligacao("ONCO11")).containsEntry("share_class", null).containsEntry("issuer_link_source", "MANUAL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unit_compositions WHERE asset_id = ?", Integer.class,
                assetId("BPAC11"))).isEqualTo(2);
    }
}

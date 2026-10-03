package com.primevest.adm.domain.issuer;

import com.primevest.domain.issuer.Issuer;
import com.primevest.domain.issuer.IssuerKind;
import com.primevest.domain.issuer.IssuerRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.adm.integration.b3.B3Client;
import com.primevest.adm.integration.b3.B3CompanyDetail;
import com.primevest.adm.integration.b3.B3Exception;
import com.primevest.adm.integration.b3.B3Fixtures;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * F1 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: emissores a partir dos recortes reais da CVM
 * (Petrobras, Itau, Klabin, Taesa, HGLG11), com o PostgreSQL de verdade.
 */
class IssuerImportIT extends IntegrationTestBase {

    @TempDir
    static Path tmp;

    @MockBean
    CvmOpenDataClient cvm;

    @MockBean
    B3Client b3;

    @Autowired
    IssuerImportService service;

    @Autowired
    IssuerRepository issuers;

    @BeforeEach
    void cenario() throws Exception {
        // issuers fica fora do TRUNCATE da base: o CASCADE levaria assets junto
        jdbc.update("UPDATE assets SET issuer_id = NULL, share_class = NULL, issuer_link_source = NULL");
        jdbc.update("DELETE FROM issuers");

        int year = LocalDate.now(ZoneId.of("America/Sao_Paulo")).getYear();
        when(cvm.companyRegistry()).thenReturn(CvmFixtures.companyRegistry(tmp));
        when(cvm.yearly(any(), anyInt())).thenReturn(Optional.empty());
        when(cvm.yearly(CvmDataset.FCA, year)).thenReturn(Optional.of(CvmFixtures.zip("fca_cia_aberta_2026", year, tmp)));
        when(cvm.yearly(CvmDataset.FII_MONTHLY, year))
                .thenReturn(Optional.of(CvmFixtures.zip("inf_mensal_fii_2026", year, tmp)));

        B3CompanyDetail petr = new ObjectMapper().readValue(B3Fixtures.read("detail_PETR.json"), B3CompanyDetail.class);
        doThrow(new B3Exception("B3 respondeu vazio")).when(b3).companyDetail(anyString());
        doReturn(petr).when(b3).companyDetail("9512");
    }

    @Test
    @DisplayName("Companhias ativas em bolsa e FIIs negociados em bolsa viram emissores")
    void importa() {
        IssuerImportService.Result r = service.importAll("teste");

        // Petrobras, Itau, Klabin, Taesa. Fora: ativa so em balcao e cancelada sem ticker no catalogo
        assertThat(r.companiesCreated()).isEqualTo(4);
        assertThat(issuers.findByCnpj("45456117000112")).isEmpty();
        assertThat(issuers.findByCnpj("60664810000174")).isEmpty();

        Issuer petr = issuers.findByCvmCode(9512).orElseThrow();
        assertThat(petr.getKind()).isEqualTo(IssuerKind.COMPANY);
        assertThat(petr.getCnpj()).isEqualTo("33000167000101");
        assertThat(petr.getTradeName()).isEqualTo("PETROBRAS");
        assertThat(petr.getFiscalYearEndMonth()).isEqualTo((short) 12);
        assertThat(petr.getListingSegment()).isEqualTo("Nível 2 de Governança Corporativa");
        assertThat(petr.getB3Sector()).isEqualTo("Petróleo, Gás e Biocombustíveis");
        assertThat(petr.getB3Segment()).isEqualTo("Exploração, Refino e Distribuição");
        // quem a B3 nao respondeu fica sem classificacao, para a proxima rodada
        assertThat(r.classified()).isEqualTo(1);
        assertThat(r.classificationFailures()).isEqualTo(3);
        assertThat(issuers.findByCvmCode(19348).orElseThrow().getB3Sector()).isNull();

        // HGLG11 entra; o fundo fora de bolsa nao
        assertThat(r.fundsCreated()).isEqualTo(1);
        Issuer hglg = issuers.findByCnpj("11728688000147").orElseThrow();
        assertThat(hglg.getKind()).isEqualTo(IssuerKind.FII);
        assertThat(hglg.getCvmCode()).isNull();
        assertThat(hglg.getCvmSegment()).isEqualTo("Multicategoria");
        assertThat(hglg.getSegment()).isNull();
        assertThat(issuers.findByCnpj("00613094000174")).isEmpty();
    }

    @Test
    @DisplayName("Rodar de novo atualiza pelo CNPJ e nunca toca o segmento curado do FII")
    void idempotente() {
        service.importAll("teste");
        Issuer hglg = issuers.findByCnpj("11728688000147").orElseThrow();
        hglg.setSegment("Imóveis Logísticos");
        issuers.save(hglg);
        long total = issuers.count();

        IssuerImportService.Result r = service.importAll("teste");

        assertThat(r.companiesCreated()).isZero();
        assertThat(r.companiesUpdated()).isEqualTo(4);
        assertThat(r.fundsCreated()).isZero();
        assertThat(issuers.count()).isEqualTo(total);
        assertThat(issuers.findByCnpj("11728688000147").orElseThrow().getSegment()).isEqualTo("Imóveis Logísticos");
        // a Petrobras ja classificada nao volta a B3
        assertThat(r.classified()).isZero();
    }
}

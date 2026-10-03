package com.primevest.adm.domain.fundamentals;

import com.primevest.adm.integration.cvm.CvmDataset;
import com.primevest.adm.integration.cvm.CvmFixtures;
import com.primevest.adm.integration.cvm.CvmOpenDataClient;
import com.primevest.domain.issuer.FiiMonthly;
import com.primevest.domain.issuer.FiiMonthlyRepository;
import com.primevest.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * F7 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: informe mensal de FII com o recorte real de 2026
 * (HGLG11 e o fundo 00.613.094/0001-74, jan a ago; os dois sao emissores no seed). O complemento traz, alem das linhas
 * reais, uma linha a mais do HGLG11 em 08/2026 com versao 1 e valores 1 — a CVM so
 * publica a versao mais recente, e a linha simula a reapresentacao chegando junto.
 */
class FiiMonthlyImportIT extends IntegrationTestBase {

    static final String HGLG = "11728688000147";
    static final String OUTRO = "00613094000174";

    @TempDir
    static Path tmp;

    @MockBean
    CvmOpenDataClient cvm;

    @Autowired
    FiiMonthlyImportService service;

    @Autowired
    FiiMonthlyRepository months;

    long hglg;

    @BeforeEach
    void cenario() {
        // os testes de emissores apagam issuers; aqui os dois fundos sao garantidos pelo CNPJ
        jdbc.update("INSERT INTO issuers (kind, cnpj, legal_name) VALUES ('FII', ?, 'CSHG LOGISTICA') "
                + "ON CONFLICT (cnpj) DO NOTHING", HGLG);
        jdbc.update("INSERT INTO issuers (kind, cnpj, legal_name) VALUES ('FII', ?, 'OUTRO FUNDO') "
                + "ON CONFLICT (cnpj) DO NOTHING", OUTRO);
        hglg = jdbc.queryForObject("SELECT id FROM issuers WHERE cnpj = ?", Long.class, HGLG);
        when(cvm.yearly(any(), anyInt())).thenReturn(Optional.empty());
        when(cvm.yearly(CvmDataset.FII_MONTHLY, 2026))
                .thenReturn(Optional.of(CvmFixtures.zip("inf_mensal_fii_2026", 2026, tmp)));
    }

    @Test
    @DisplayName("Oito meses de cada fundo: complemento e carteira juntos, versao mais alta")
    void importa() {
        FiiMonthlyImportService.Result r = service.importYears(2016, 2026, false, "teste");

        assertThat(r.months()).isEqualTo(16);
        assertThat(r.created()).isEqualTo(16);
        assertThat(r.funds()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fii_monthly", Integer.class)).isEqualTo(16);

        List<FiiMonthly> serie = months.findByIssuerIdOrderByReferenceMonth(hglg);
        assertThat(serie).extracting(FiiMonthly::getReferenceMonth)
                .first().isEqualTo(LocalDate.of(2026, 1, 1));

        FiiMonthly ago = serie.get(7);
        assertThat(ago.getReferenceMonth()).isEqualTo(LocalDate.of(2026, 8, 1));
        // a versao 2 ganha da linha de versao 1 do mesmo mes
        assertThat(ago.getVersion()).isEqualTo(2);
        assertThat(ago.getNetAssets()).isEqualByComparingTo("7567668488.14");
        assertThat(ago.getTotalAssets()).isEqualByComparingTo("8850561499.23");
        assertThat(ago.getSharesIssued()).isEqualByComparingTo("45601713");
        assertThat(ago.getNavPerShare()).isEqualByComparingTo("165.951408188109");
        assertThat(ago.getShareholders()).isEqualTo(608345);
        assertThat(ago.getAdminFeeMonth()).isEqualByComparingTo("0.000442");
        assertThat(ago.getDividendYieldMonth()).isEqualByComparingTo("0.007027");
        assertThat(ago.getAmortizationMonth()).isEqualByComparingTo("0");
        // carteira
        assertThat(ago.getLiquidityTotal()).isEqualByComparingTo("42542054.5");
        assertThat(ago.getTotalInvested()).isEqualByComparingTo("8289415918.47");
        assertThat(ago.getRealEstateTotal()).isEqualByComparingTo("7057544916.78");
        assertThat(ago.getRealEstateCompanies()).isEqualByComparingTo("1051691815.4");
        assertThat(ago.getReceivablesTotal()).isEqualByComparingTo("31703049.55");
        assertThat(ago.getFiiSharesTotal()).isEqualByComparingTo("148476136.74");
        assertThat(ago.getLiabilitiesTotal()).isEqualByComparingTo("1282893011.09");
        assertThat(ago.getIncomeToDistribute()).isEqualByComparingTo("53331888.87");
        // taxa em notacao cientifica no arquivo (7.37453695588709E-06 no custodiante) nao derruba a leitura
        assertThat(serie.get(3).getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("Rodar de novo nao regrava; force regrava tudo")
    void idempotente() {
        service.importYears(2026, 2026, false, "teste");
        jdbc.update("UPDATE fii_monthly SET net_assets = 1 WHERE issuer_id = ? AND reference_month = '2026-01-01'",
                hglg);

        FiiMonthlyImportService.Result again = service.importYears(2026, 2026, false, "teste");
        assertThat(again.created()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(again.unchanged()).isEqualTo(16);
        assertThat(netAssetsJan()).isEqualByComparingTo("1");

        FiiMonthlyImportService.Result forced = service.importYears(2026, 2026, true, "teste");
        assertThat(forced.updated()).isEqualTo(16);
        assertThat(netAssetsJan()).isNotEqualByComparingTo("1");
    }

    @Test
    @DisplayName("Versao nova de um mes ja gravado substitui a linha")
    void reapresentacao() {
        service.importYears(2026, 2026, false, "teste");
        jdbc.update("UPDATE fii_monthly SET version = 1, net_assets = 1 WHERE issuer_id = ? "
                + "AND reference_month = '2026-04-01'", hglg);

        FiiMonthlyImportService.Result r = service.importYears(2026, 2026, false, "teste");

        assertThat(r.updated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT net_assets FROM fii_monthly WHERE issuer_id = ? "
                + "AND reference_month = '2026-04-01'", java.math.BigDecimal.class, hglg))
                .isEqualByComparingTo("7234911197.7");
    }

    private java.math.BigDecimal netAssetsJan() {
        return jdbc.queryForObject("SELECT net_assets FROM fii_monthly WHERE issuer_id = ? "
                + "AND reference_month = '2026-01-01'", java.math.BigDecimal.class, hglg);
    }
}

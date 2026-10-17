package com.primevest.adm.domain.fixedincome;

import com.primevest.adm.integration.bcb.BcbFixtures;
import com.primevest.adm.integration.bcb.BcbInstitutionsClient;
import com.primevest.adm.integration.cvm.CvmCompany;
import com.primevest.adm.integration.cvm.CvmDataset;
import com.primevest.adm.integration.cvm.CvmFixtures;
import com.primevest.adm.integration.cvm.CvmIntermediary;
import com.primevest.adm.integration.cvm.CvmOpenDataClient;
import com.primevest.domain.fixedincome.FixedIncomeGroup;
import com.primevest.domain.fixedincome.FixedIncomeGroupRepository;
import com.primevest.domain.fixedincome.FixedIncomeIssuer;
import com.primevest.domain.fixedincome.FixedIncomeIssuerKind;
import com.primevest.domain.fixedincome.FixedIncomeIssuerRepository;
import com.primevest.domain.fixedincome.Guarantor;
import com.primevest.domain.fixedincome.SaleRule;
import com.primevest.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Cadastro de emissoras e distribuidoras de renda fixa a partir dos recortes do BCB e da CVM, com o PostgreSQL de
 * verdade (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, secao 4).
 */
class FixedIncomeIssuerImportIT extends IntegrationTestBase {

    @TempDir
    static Path tmp;

    @MockBean
    BcbInstitutionsClient bcb;

    @MockBean
    CvmOpenDataClient cvm;

    @Autowired
    FixedIncomeIssuerImportService service;

    @Autowired
    FixedIncomeIssuerRepository issuers;

    @Autowired
    FixedIncomeGroupRepository groups;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void cenario() throws Exception {
        jdbc.update("DELETE FROM fixed_income_issuers WHERE cnpj_root IS NOT NULL");
        jdbc.update("DELETE FROM fixed_income_groups");

        int year = LocalDate.now(ZoneId.of("America/Sao_Paulo")).getYear();
        when(bcb.latestRegistry()).thenReturn(BcbFixtures.registry());
        when(bcb.websites()).thenReturn(BcbFixtures.websites());
        when(cvm.intermediaryRegistry()).thenReturn(CvmFixtures.zip("cad_intermed", year, tmp));
        when(cvm.companyRegistry()).thenReturn(CvmFixtures.companyRegistry(tmp));
        when(cvm.yearly(any(), anyInt())).thenReturn(Optional.empty());
        when(cvm.yearly(CvmDataset.SECURIT_CRI, year))
                .thenReturn(Optional.of(CvmFixtures.zip("inf_mensal_cri_2026", year, tmp)));
    }

    private FixedIncomeIssuer porRaiz(String raiz) {
        return tx.execute(s -> {
            FixedIncomeIssuer i = issuers.findByCnpjRoot(raiz).orElseThrow();
            if (i.getGroup() != null) {
                i.getGroup().getName();
            }
            return i;
        });
    }

    @Test
    @DisplayName("BCB: bancos, financeiras, cooperativas e corretoras entram (vivas e mortas); instituicao de pagamento fica fora")
    void bcb() {
        FixedIncomeIssuerImportService.Result r = service.importAll("teste");

        assertThat(r.baseMonth()).isEqualTo("202606");
        // 10 instituicoes no recorte; Nu Pagamentos e Stone sao instituicoes de pagamento (D1 b)
        assertThat(r.bcbInstitutions()).isEqualTo(8);
        assertThat(issuers.findByCnpjRoot("18236120")).isEmpty();
        assertThat(issuers.findByCnpjRoot("16501555")).isEmpty();

        FixedIncomeIssuer nuFinanceira = porRaiz("30680829");
        assertThat(nuFinanceira.getKind()).isEqualTo(FixedIncomeIssuerKind.FINANCEIRA);
        assertThat(nuFinanceira.getGuarantor()).isEqualTo(Guarantor.FGC);
        assertThat(nuFinanceira.getStatus()).isEqualTo("ATIVA");
        assertThat(nuFinanceira.getWebsite()).isEqualTo("www.nubank.com.br");
        assertThat(nuFinanceira.getGroup().getBcbCode()).isEqualTo("C0084693");
        assertThat(nuFinanceira.getGroupSource()).isEqualTo(FixedIncomeIssuer.GroupSource.BCB);

        assertThat(porRaiz("62169875").getKind()).isEqualTo(FixedIncomeIssuerKind.CORRETORA_DISTRIBUIDORA);
        assertThat(porRaiz("72500705").getKind()).isEqualTo(FixedIncomeIssuerKind.COOPERATIVA);
        assertThat(porRaiz("72500705").getGuarantor()).isEqualTo(Guarantor.FGCOOP);
        // banco cooperativo: BANCO, mas FGCoop
        assertThat(porRaiz("01181521").getKind()).isEqualTo(FixedIncomeIssuerKind.BANCO);
        assertThat(porRaiz("01181521").getGuarantor()).isEqualTo(Guarantor.FGCOOP);
        // mortas continuam no cadastro com a situacao (R3)
        assertThat(porRaiz("33884941").getStatus()).isEqualTo("LIQUIDACAO_EXTRAJUDICIAL");
        assertThat(porRaiz("39544456").getStatus()).isEqualTo("INATIVA");
    }

    @Test
    @DisplayName("Grupos: nome automatico do BCB (Nubank fica 'NU PAGAMENTOS' ate a curadoria), lider e descricao")
    void grupos() {
        service.importAll("teste");

        FixedIncomeGroup nubank = groups.findByBcbCode("C0084693").orElseThrow();
        assertThat(nubank.getName()).isEqualTo("NU Pagamentos");
        assertThat(nubank.getNameSource()).isEqualTo(FixedIncomeGroup.NameSource.AUTOMATICO);
        assertThat(nubank.getDescription()).isEqualTo("NU PAGAMENTOS - PRUDENCIAL");
        assertThat(nubank.getLeaderCnpjRoot()).isEqualTo("18236120");
        assertThat(groups.findByBcbCode("C0082475").orElseThrow().getName()).isEqualTo("XP");
        // grupo sem nenhuma empresa no cadastro (so a Stone, instituicao de pagamento) nao e criado
        assertThat(groups.findByBcbCode("C0084686")).isEmpty();
    }

    @Test
    @DisplayName("CVM: intermediario completa o do BCB (CNPJ, nome comercial) e cria o que so a CVM tem; companhias e securitizadoras entram")
    void cvm() {
        FixedIncomeIssuerImportService.Result r = service.importAll("teste");

        FixedIncomeIssuer nuInvest = porRaiz("62169875");
        assertThat(nuInvest.getCnpj()).isEqualTo("62169875000179");
        assertThat(nuInvest.getTradeName()).isEqualTo("Nu Invest");
        assertThat(nuInvest.getSources()).isEqualTo("BCB,CVM_INTERMED");
        // a Warren nao esta no recorte do BCB: entra pela CVM
        FixedIncomeIssuer warren = porRaiz("92875780");
        assertThat(warren.getKind()).isEqualTo(FixedIncomeIssuerKind.CORRETORA_DISTRIBUIDORA);
        assertThat(warren.getStatus()).isEqualTo("ATIVA");
        assertThat(warren.getSources()).isEqualTo("CVM_INTERMED");

        assertThat(porRaiz("33000167").getKind()).isEqualTo(FixedIncomeIssuerKind.COMPANHIA);
        assertThat(porRaiz("60664810").getStatus()).isEqualTo("CANCELADA");
        FixedIncomeIssuer habitasec = porRaiz("09304427");
        assertThat(habitasec.getKind()).isEqualTo(FixedIncomeIssuerKind.SECURITIZADORA);
        assertThat(habitasec.getLegalName()).isEqualTo("HABITASEC SECURITIZADORA S.A.");
        assertThat(r.securitizers()).isEqualTo(2);
    }

    @Test
    @DisplayName("O curado fica (R9): nome MANUAL, ligacao MANUAL, regra de saida e logo; quem perde o conglomerado mantem o grupo")
    void naoSobrescreveOCurado() {
        service.importAll("teste");
        tx.executeWithoutResult(s -> {
            FixedIncomeGroup nubank = groups.findByBcbCode("C0084693").orElseThrow();
            nubank.setName("Nubank");
            nubank.setNameSource(FixedIncomeGroup.NameSource.MANUAL);
            nubank.setLogoTicker("ROXO34");
            FixedIncomeIssuer nuFinanceira = issuers.findByCnpjRoot("30680829").orElseThrow();
            nuFinanceira.setSaleRule(SaleRule.UEPS);
            // a Nu DTVM, inativa, ja nao tem conglomerado no BCB: a curadoria a liga ao grupo antigo
            FixedIncomeIssuer nuDtvm = issuers.findByCnpjRoot("39544456").orElseThrow();
            nuDtvm.setGroup(nubank);
            nuDtvm.setGroupSource(FixedIncomeIssuer.GroupSource.MANUAL);
            // e o Banco XP vai para um grupo de curadoria qualquer
            FixedIncomeGroup outro = groups.save(FixedIncomeGroup.builder().name("Outro")
                    .nameSource(FixedIncomeGroup.NameSource.MANUAL).build());
            FixedIncomeIssuer bancoXp = issuers.findByCnpjRoot("33264668").orElseThrow();
            bancoXp.setGroup(outro);
            bancoXp.setGroupSource(FixedIncomeIssuer.GroupSource.MANUAL);
        });

        FixedIncomeIssuerImportService.Result r = service.importAll("teste de novo");

        FixedIncomeGroup nubank = groups.findByBcbCode("C0084693").orElseThrow();
        assertThat(nubank.getName()).isEqualTo("Nubank");
        assertThat(nubank.getLogoTicker()).isEqualTo("ROXO34");
        assertThat(porRaiz("30680829").getSaleRule()).isEqualTo(SaleRule.UEPS);
        assertThat(porRaiz("39544456").getGroup().getName()).isEqualTo("Nubank");
        assertThat(porRaiz("33264668").getGroup().getName()).isEqualTo("Outro");
        // idempotente: nada novo na segunda rodada
        assertThat(r.issuersCreated()).isZero();
        assertThat(r.groupsCreated()).isZero();
    }

    @Test
    @DisplayName("Empresa que perde o conglomerado na fonte mantem o grupo que tinha (ligacao BCB)")
    void mortaMantemOGrupo() {
        service.importAll("teste");
        BcbInstitutionsClient.Registry sem = new BcbInstitutionsClient.Registry("202609",
                BcbFixtures.registry().institutions().stream()
                        .map(i -> i.cnpjRoot().equals("62169875")
                                ? new com.primevest.adm.integration.bcb.BcbInstitution(i.cnpjRoot(), i.name(),
                                        i.segment(), "I", null, null)
                                : i).toList(),
                BcbFixtures.registry().groups());
        when(bcb.latestRegistry()).thenReturn(sem);

        service.importAll("teste de novo");

        FixedIncomeIssuer nuInvest = porRaiz("62169875");
        assertThat(nuInvest.getStatus()).isEqualTo("INATIVA");
        assertThat(nuInvest.getGroup().getBcbCode()).isEqualTo("C0084693");
    }

    @Test
    @DisplayName("Companhia que tambem e securitizadora (informe de CRI) vira SECURITIZADORA")
    void companhiaSecuritizadora() {
        BcbInstitutionsClient.Registry vazio = new BcbInstitutionsClient.Registry("202606", List.of(), Map.of());
        Map<String, CvmCompany> companhias = Map.of("07859971000130", new CvmCompany("07859971000130", 20257,
                "TRANSMISSORA ALIANÇA DE ENERGIA ELÉTRICA S.A.", "TAESA", "ATIVO", "Energia Elétrica", "Categoria A", true));

        tx.executeWithoutResult(s -> service.persist(vazio, Map.of(), Map.<String, CvmIntermediary>of(), companhias,
                Map.of("07859971000130", "TAESA")));

        assertThat(porRaiz("07859971").getKind()).isEqualTo(FixedIncomeIssuerKind.SECURITIZADORA);
        assertThat(porRaiz("07859971").getLegalName()).isEqualTo("TRANSMISSORA ALIANÇA DE ENERGIA ELÉTRICA S.A.");
    }

    @Test
    @DisplayName("As duas linhas fixas (Tesouro Nacional, nao listada) nao sao tocadas")
    void linhasFixas() throws Exception {
        service.importAll("teste");

        assertThat(issuers.findFirstByKind(FixedIncomeIssuerKind.TESOURO).orElseThrow().getLegalName())
                .isEqualTo("Tesouro Nacional");
        assertThat(issuers.findFirstByKind(FixedIncomeIssuerKind.NAO_LISTADA)).isPresent();
        assertThat(Files.exists(tmp)).isTrue();
    }
}

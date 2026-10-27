package com.activest.adm.domain.fund;

import com.activest.adm.integration.PublicFileCache;
import com.activest.adm.integration.cvm.CvmFundFiles;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Cadastro e cota dos fundos da CVM (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, F2–F4) com arquivos pequenos no formato
 * real: a classe do fundo previdenciário do Guilherme (com a denominação comercial e o nome antigo), uma classe com
 * subclasse, um fundo do formato antigo cancelado que ainda informa cota e uma chave só do informe.
 */
class FundCatalogIT extends IntegrationTestBase {

    @TempDir
    static Path dir;

    @MockBean
    CvmFundFiles files;

    @Autowired
    FundCatalogService service;

    static final LocalDate QUOTE_DAY = LocalDate.now().minusDays(2);
    static final YearMonth MONTH = YearMonth.from(QUOTE_DAY);

    @BeforeEach
    void files() throws Exception {
        Files.createDirectories(dir.resolve("inf_diario"));
        zip(dir.resolve("registro_fundo_classe.zip"), Map.of(
            "registro_fundo.csv", """
                ID_Registro_Fundo;CNPJ_Fundo;Codigo_CVM;Data_Registro;Data_Constituicao;Tipo_Fundo;Denominacao_Social;Data_Cancelamento;Situacao;Data_Inicio_Situacao;Data_Adaptacao_RCVM175;Data_Inicio_Exercicio_Social;Data_Fim_Exercicio_Social;Patrimonio_Liquido;Data_Patrimonio_Liquido;Diretor;CNPJ_Administrador;Administrador;Tipo_Pessoa_Gestor;CPF_CNPJ_Gestor;Gestor
                20077;03926431000171;26913;2003-04-30;2000-09-22;FIF;CAIXA 300 PREVIDENCIÁRIO FIC DE CLASSE DE FIF RENDA FIXA - RESPONSABILIDADE LIMITADA;2004-12-22;Cancelado;2004-12-22;;;;1319129614.30;2004-12-21;;;;;;
                29784;03926431000171;1694;2004-12-30;2000-09-22;FI;CAIXA 300 PREVIDENCIÁRIO FIC DE CLASSE DE FIF RENDA FIXA - RESPONSABILIDADE LIMITADA;;Em Funcionamento Normal;2004-12-22;2025-03-19;2026-01-01;2026-12-31;4872772081.51;2025-03-19;RICARDO;00360305000104;CAIXA ECONOMICA FEDERAL;PJ;03730204000176;CAIXA VIDA E PREVIDÊNCIA S/A
                500;11111111000111;1;2020-01-01;2020-01-01;FI;MULTI TESTE FUNDO DE INVESTIMENTO MULTIMERCADO;;Em Funcionamento Normal;2020-01-01;2025-01-01;;;10;2026-01-01;;22222222000122;ADMIN X;PJ;33333333000133;GESTOR X
                600;00000684000121;19;2003-04-30;1994-12-20;FACFIF;DEUTSCHE BANK FDO APLIC QUOTAS FDO INV FINANCEIRO - MAX;2000-08-01;Cancelado;2000-08-01;;;;;;;;;;;
                700;66666666000166;70;2010-01-01;2010-01-01;FI;VELHO FUNDO DE INVESTIMENTO MULTIMERCADO;2019-01-01;Cancelado;2019-01-01;;;;;;;;;;;
                701;66666666000166;71;2019-06-01;2019-06-01;FIDC;NOVO FIDC;;Em Funcionamento Normal;2019-06-01;;;;;;;;;;;
                """,
            "registro_classe.csv", """
                ID_Registro_Fundo;ID_Registro_Classe;CNPJ_Classe;Codigo_CVM;Data_Registro;Data_Constituicao;Data_Inicio;Tipo_Classe;Denominacao_Social;Situacao;Data_Inicio_Situacao;Classificacao;Indicador_Desempenho;Classe_Cotas;Classificacao_Anbima;Tributacao_Longo_Prazo;Entidade_Investimento;Permitido_Aplicacao_CemPorCento_Exterior;Classe_ESG;Forma_Condominio;Exclusivo;Publico_Alvo;Patrimonio_Liquido;Data_Patrimonio_Liquido
                29784;581;03926431000171;129437;2025-03-19;2000-09-22;2025-03-19;Classes de Cotas de Fundos FIF;CAIXA 300 PREVIDENCIÁRIO FIC DE CLASSE DE FIF RENDA FIXA - RESPONSABILIDADE LIMITADA;Em Funcionamento Normal;2025-03-19;Renda Fixa;;S;Previdência RF Duração Livre Grau de Inv;N;;N;N;Aberto;S;Profissional;4950958460.00;2026-10-20
                500;900;11111111000111;2;2025-01-01;2020-01-01;2025-01-01;Classes de Cotas de Fundos FIF;MULTI TESTE CLASSE DE FIF MULTIMERCADO - RESPONSABILIDADE LIMITADA;Em Funcionamento Normal;2025-01-01;Multimercado;;N;Multimercados Livre;S;;N;N;Aberto;N;Público Geral;10;2026-01-01
                501;901;44444444000144;3;2025-01-01;2020-01-01;2025-01-01;Classes de Cotas de Fundos FIDC;UM FIDC;Em Funcionamento Normal;2025-01-01;;;N;;S;;N;N;Fechado;N;Profissional;10;2026-01-01
                """,
            "registro_subclasse.csv", """
                ID_Registro_Classe;ID_Subclasse;Codigo_CVM;Data_Constituicao;Data_Inicio;Denominacao_Social;Situacao;Data_Inicio_Situacao;Forma_Condominio;Exclusivo;Publico_Alvo;Previdenciario
                900;SUB1;3;2025-01-01;2025-01-01;SUBCLASSE A;Em Funcionamento Normal;2025-01-01;Aberto;N;Público Geral;N
                """));
        zip(dir.resolve("cad_fi_hist.zip"), Map.of(
            "cad_fi_hist_denom_social.csv", """
                CNPJ_FUNDO;DT_REG;DENOM_SOCIAL;DT_INI_DENOM_SOCIAL;DT_FIM_DENOM_SOCIAL
                03.926.431/0001-71;2004-12-30;FUNDO DE INVESTIMENTO EM COTAS DE FUNDOS DE INVESTIMENTO PREVIDENCIÁRIO CAIXA RENDA FIXA 300;2011-07-19;2025-03-19
                03.926.431/0001-71;2004-12-30;FUNDO PREVINVEST CAIXA DE INVESTIMENTO FINANCEIRO-RENDA FIXA;2000-02-29;2004-12-22
                """,
            "cad_fi_hist_denom_comerc.csv", """
                CNPJ_FUNDO;DT_REG;DENOM_COMERC;DT_INI_DENOM_COMERC;DT_FIM_DENOM_COMERC
                03.926.431/0001-71;2004-12-30;CAIXA FIC PREVIDÊNCIA RF 300;2011-07-19;2011-08-17
                03.926.431/0001-71;2004-12-30;CAIXA FIC PREV 300 RF;2011-08-17;
                """));
        Files.writeString(dir.resolve("cad_fi.csv"), """
            TP_FUNDO;CNPJ_FUNDO;DENOM_SOCIAL;DT_REG;DT_CONST;CD_CVM;DT_CANCEL;SIT;DT_INI_SIT;DT_INI_ATIV;DT_INI_EXERC;DT_FIM_EXERC;CLASSE;DT_INI_CLASSE;RENTAB_FUNDO;CONDOM;FUNDO_COTAS;FUNDO_EXCLUSIVO;TRIB_LPRAZO;PUBLICO_ALVO;ENTID_INVEST;TAXA_PERFM;INF_TAXA_PERFM;TAXA_ADM;INF_TAXA_ADM;VL_PATRIM_LIQ;DT_PATRIM_LIQ;DIRETOR;CNPJ_ADMIN;ADMIN;PF_PJ_GESTOR;CPF_CNPJ_GESTOR;GESTOR;CNPJ_AUDITOR;AUDITOR;CNPJ_CUSTODIANTE;CUSTODIANTE;CNPJ_CONTROLADOR;CONTROLADOR;INVEST_CEMPR_EXTER;CLASSE_ANBIMA
            FACFIF;00.000.684/0001-21;DEUTSCHE BANK FDO APLIC QUOTAS FDO INV FINANCEIRO - MAX;2003-04-30;1994-12-20;19;2000-08-01;CANCELADA;2000-08-01;;;;Renda Fixa;;;Aberto;N;N;S;;;;;;;;;;;;;;;;;;;;;;
            """, StandardCharsets.ISO_8859_1);
        String day = QUOTE_DAY.toString();
        String before = QUOTE_DAY.minusDays(1).toString();
        String month = MONTH.getYear() + String.format("%02d", MONTH.getMonthValue());
        Path monthly = dir.resolve("inf_diario").resolve("inf_diario_fi_" + month + ".zip");
        zip(monthly, Map.of("inf_diario_fi_" + month + ".csv", """
            TP_FUNDO_CLASSE;CNPJ_FUNDO_CLASSE;ID_SUBCLASSE;DT_COMPTC;VL_TOTAL;VL_QUOTA;VL_PATRIM_LIQ;CAPTC_DIA;RESG_DIA;NR_COTST
            CLASSES - FIF;03.926.431/0001-71;;%s;1;9.670000000000;1;0;0;1
            CLASSES - FIF;03.926.431/0001-71;;%s;1;9.680832831000;1;0;0;1
            CLASSES - FIF;11.111.111/0001-11;SUB1;%s;1;1.500000000000;1;0;0;3
            FI;00.000.684/0001-21;;%s;1;2.000000000000;1;0;0;3
            CLASSES - FIF;55.555.555/0001-55;;%s;1;3.000000000000;1;0;0;3
            CLASSES - FIF;11.111.111/0001-11;;%s;1;90335366100.00;1;0;0;3
            FI;66.666.666/0001-66;;%s;1;4.000000000000;1;0;0;3
            """.formatted(before, day, day, day, day, day, QUOTE_DAY.minusDays(60))));
        when(files.dir()).thenReturn(dir);
        when(files.dailyDir()).thenReturn(dir.resolve("inf_diario"));
        when(files.registry(anyBoolean())).thenReturn(notModified(dir.resolve("registro_fundo_classe.zip")));
        when(files.registryHistory(anyBoolean())).thenReturn(notModified(dir.resolve("cad_fi_hist.zip")));
        when(files.oldRegistry(anyBoolean())).thenReturn(notModified(dir.resolve("cad_fi.csv")));
        when(files.monthly(any(), anyBoolean())).thenAnswer(inv -> {
            YearMonth m = inv.getArgument(0);
            return m.equals(MONTH) ? notModified(monthly)
                    : new PublicFileCache.Result(PublicFileCache.Outcome.NOT_FOUND, null, "HTTP 404", 404, null);
        });
        when(files.yearly(anyInt())).thenReturn(
                new PublicFileCache.Result(PublicFileCache.Outcome.NOT_FOUND, null, "HTTP 404", 404, null));
        when(files.localDailyFiles()).thenReturn(List.of(monthly));
    }

    private static PublicFileCache.Result notModified(Path file) {
        return new PublicFileCache.Result(PublicFileCache.Outcome.NOT_MODIFIED, file, null, 304, null);
    }

    private static void zip(Path target, Map<String, String> entries) throws Exception {
        try (OutputStream out = Files.newOutputStream(target); ZipOutputStream z = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.ISO_8859_1));
                z.closeEntry();
            }
        }
    }

    @AfterEach
    void clear() {
        jdbc.update("DELETE FROM asset_price_history WHERE source = 'CVM'");
        jdbc.update("DELETE FROM assets WHERE asset_type_id = (SELECT id FROM asset_types WHERE name = 'FUNDO_INVESTIMENTO')");
        jdbc.update("DELETE FROM notifications WHERE kind IN ('FONTE_INDISPONIVEL', 'FONTE_DESATUALIZADA')");
    }

    private Map<String, Object> fund(String ticker) {
        return jdbc.queryForMap("""
            SELECT a.id, a.name, a.active, a.current_price, c.name AS category, f.*
              FROM assets a JOIN investment_funds f ON f.asset_id = a.id
              JOIN asset_categories c ON c.id = a.asset_category_id WHERE a.ticker = ?""", ticker);
    }

    @Test
    @DisplayName("Rodada diaria: classe, subclasse, fundo antigo e chave so do informe viram ativos; FIDC fica fora")
    void daily() {
        FundCatalogService.Result r = service.runDaily(null, "teste");

        assertThat(r.funds()).isEqualTo(6);
        assertThat(r.created()).isEqualTo(6);
        assertThat(r.lastDate()).isEqualTo(QUOTE_DAY);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE ticker = '44444444000144'", Integer.class)).isZero();

        Map<String, Object> caixa = fund("03926431000171");
        assertThat(caixa.get("short_name")).isEqualTo("CAIXA FIC PREV 300 RF");
        assertThat(caixa.get("name")).isEqualTo("CAIXA 300 PREVIDENCIÁRIO FIC DE CLASSE DE FIF RENDA FIXA - RESPONSABILIDADE LIMITADA");
        assertThat(caixa.get("category")).isEqualTo("Previdência");
        assertThat(caixa.get("active")).isEqualTo(true);
        assertThat(caixa.get("exclusive")).isEqualTo(true);
        assertThat(caixa.get("target_audience")).isEqualTo("Profissional");
        assertThat(caixa.get("long_term_tax")).isEqualTo(false);
        assertThat(caixa.get("manager_name")).isEqualTo("CAIXA VIDA E PREVIDÊNCIA S/A");
        assertThat((BigDecimal) caixa.get("current_price")).isEqualByComparingTo("9.68083283");
        assertThat(caixa.get("last_quote_on").toString()).isEqualTo(QUOTE_DAY.toString());
        assertThat((String) caixa.get("search_text")).contains("03.926.431/0001-71")
            .contains("FUNDO DE INVESTIMENTO EM COTAS DE FUNDOS DE INVESTIMENTO PREVIDENCIARIO CAIXA RENDA FIXA 300")
            .contains("PREVINVEST");

        Map<String, Object> sub = fund("11111111000111-SUB1");
        assertThat(sub.get("name")).isEqualTo("MULTI TESTE CLASSE DE FIF MULTIMERCADO - RESPONSABILIDADE LIMITADA - SUBCLASSE A");
        assertThat(sub.get("parent_asset_id")).isEqualTo(fund("11111111000111").get("id"));
        assertThat(sub.get("category")).isEqualTo("Multimercado");

        // cancelado no cadastro, mas informou cota nos ultimos 30 dias: vivo (C2); classificacao do cad_fi.csv
        Map<String, Object> old = fund("00000684000121");
        assertThat(old.get("active")).isEqualTo(true);
        assertThat(old.get("category")).isEqualTo("Renda Fixa");
        assertThat(old.get("long_term_tax")).isEqualTo(true);

        Map<String, Object> unknown = fund("55555555000155");
        assertThat(unknown.get("name")).isEqualTo("Fundo 55.555.555/0001-55");
        assertThat(unknown.get("category")).isEqualTo("Outros");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM investment_fund_names n JOIN assets a ON a.id = n.asset_id "
            + "WHERE a.ticker = '03926431000171'", Integer.class)).isEqualTo(5);

        // cota absurda do informe (acima de 10^10, a coluna de preco) e descartada: a classe fica sem cota
        assertThat(fund("11111111000111").get("current_price")).isNull();

        // CNPJ com FI cancelado (o que informou a cota) e FIDC vivo: o fundo e o FI, encerrado (D1, C2)
        Map<String, Object> reused = fund("66666666000166");
        assertThat(reused.get("fund_kind")).isEqualTo("FI");
        assertThat(reused.get("name")).isEqualTo("VELHO FUNDO DE INVESTIMENTO MULTIMERCADO");
        assertThat(reused.get("active")).isEqualTo(false);

        // segunda rodada: nada novo, mesmos numeros
        FundCatalogService.Result again = service.runDaily(null, "teste");
        assertThat(again.created()).isZero();
        assertThat(again.funds()).isEqualTo(6);
    }

    @Test
    @DisplayName("Carga inicial (etapa do BOOTSTRAP): cadastro e ultima cota de todas as chaves do informe, sem serie (D5)")
    void bootstrap() {
        FundCatalogService.Result r = service.bootstrap("teste");

        assertThat(r.funds()).isEqualTo(6);
        assertThat(r.seriesRows()).isZero();
        assertThat(r.lastDate()).isEqualTo(QUOTE_DAY);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_price_history WHERE source = 'CVM'", Integer.class))
            .isZero();
        assertThat((BigDecimal) fund("03926431000171").get("current_price")).isEqualByComparingTo("9.68083283");
        // de novo: nada novo (retomavel, como as demais etapas)
        assertThat(service.bootstrap("teste").created()).isZero();
    }

    @Test
    @DisplayName("Serie sob demanda: as cotas do fundo entram uma vez; ativo que nao e fundo e ignorado")
    void series() {
        service.runDaily(null, "teste");
        Long id = ((Number) fund("03926431000171").get("id")).longValue();

        assertThat(service.loadSeries(id, "teste")).isEqualTo(2);
        // de novo: nada a gravar, sem duplicar (F4)
        assertThat(service.loadSeries(id, "teste")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_price_history WHERE asset_id = ?", Integer.class, id))
            .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT close_price FROM asset_price_history WHERE asset_id = ? AND reference_date = ?",
            BigDecimal.class, id, QUOTE_DAY)).isEqualByComparingTo("9.68083283");
        Long petr4 = jdbc.queryForObject("SELECT id FROM assets WHERE ticker = 'PETR4'", Long.class);
        assertThat(service.loadSeries(petr4, "teste")).isZero();
    }

    @Test
    @DisplayName("Sem nenhum arquivo do informe: rodada falha e avisa o administrador")
    void unavailable() {
        doReturn(new PublicFileCache.Result(PublicFileCache.Outcome.UNREACHABLE, null, "timeout", null, null))
            .when(files).monthly(any(), anyBoolean());
        assertThatThrownBy(() -> service.runDaily(null, "teste")).isInstanceOf(PublicFileCache.UnavailableException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'FONTE_INDISPONIVEL'",
            Integer.class)).isEqualTo(1);
    }
}

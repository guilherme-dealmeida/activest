package com.activest.adm.domain.asset;

import com.activest.adm.domain.notice.AdminNotices;
import com.activest.adm.integration.cvm.CvmCancellation;
import com.activest.adm.integration.cvm.CvmOpenDataClient;
import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetTypeNames;
import com.activest.domain.asset.DelistingSource;
import com.activest.domain.notification.NotificationKind;
import com.activest.domain.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inativação automática do nacional na conferência do catálogo (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, I5 e C7 —
 * etapa F2). Só com prova:
 *
 * <ul>
 *   <li><b>CVM</b> — registro da companhia cancelado na CVM, menos "migração de registro" (que não é saída de bolsa):
 *       inativa os tickers da companhia;</li>
 *   <li><b>LIST_GONE</b> — o ticker (e o papel inteiro, no fracionário) saiu da lista oficial da BRAPI <b>e</b> está sem
 *       negócio no COTAHIST há {@value #SESSIONS} pregões, sem ser renomeação. Suspensão de negociação não inativa: o
 *       papel suspenso continua na lista oficial.</li>
 * </ul>
 *
 * <p>Data de saída: o último pregão com negócio no COTAHIST (o do papel ou o do papel inteiro, no fracionário); sem
 * pregão, a data do cancelamento na CVM. A decisão do administrador ({@code MANUAL}) nunca é refeita. Cada inativação vira
 * um aviso de administrador com a prova ({@link NotificationKind#CATALOGO_TICKER_INATIVADO}), que chega a quem tem
 * posição; o administrador desfaz pela curadoria ({@code PUT /api/v1/admin/assets/{id}/activation}).</p>
 *
 * <p>Também dá a data de saída aos inativos que ainda não têm (I5, 1ª parte: os 9 inativos da B3 de antes da F2).</p>
 */
@Slf4j
@Component
public class CatalogDelisting {

    /** Pregões sem negócio para a lista vazia virar saída (10.7.3). */
    static final int SESSIONS = 30;
    /** COTAHIST mais velho que isto não serve de prova (o diário parou): a regra do sumiço não roda. */
    static final int MAX_COTAHIST_AGE_DAYS = 10;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;
    private final CvmOpenDataClient cvm;
    private final AdminNotices notices;

    public CatalogDelisting(JdbcTemplate jdbc, CvmOpenDataClient cvm, AdminNotices notices) {
        this.jdbc = jdbc;
        this.cvm = cvm;
        this.notices = notices;
    }

    /** O que a rodada fez. */
    public record Result(int byCvm, int byListGone, int datesFilled) {
    }

    /**
     * @param catalog    ativos ativos do catálogo, com o tipo
     * @param official   lista oficial da BRAPI (ticker em maiúsculas); vazia = indisponível, e a regra do sumiço não roda
     * @param renamedOld tickers antigos de renomeação da BRAPI; nulo = renomes indisponíveis (a regra do sumiço não roda)
     * @param ignored    tickers fora do catálogo de propósito ({@code activest.catalog-audit.ignored})
     * @param held       ativos com posição aberta (vai no aviso)
     */
    public Result run(List<Asset> catalog, Map<String, String> official, Set<String> renamedOld, Set<String> ignored,
                      Set<Long> held) {
        LocalDate today = NotificationService.today();
        Map<Long, LocalDate> lastTrade = lastTrades();
        Map<String, Long> idByTicker = new HashMap<>();
        catalog.stream().filter(CatalogDelisting::b3)
                .forEach(a -> idByTicker.put(a.getTicker().toUpperCase(), a.getId()));
        Set<Long> manual = Set.copyOf(jdbc.queryForList(
                "SELECT id FROM assets WHERE delisting_source = 'MANUAL'", Long.class));

        // 1. CVM: registro cancelado (menos migracao)
        int byCvm = 0;
        Map<String, CvmCancellation> cancelled = cancellations();
        Map<Long, String> cnpjByAsset = new HashMap<>();
        if (!cancelled.isEmpty()) {
            jdbc.query("""
                    SELECT a.id, i.cnpj FROM assets a JOIN issuers i ON i.id = a.issuer_id
                     WHERE a.active AND i.kind = 'COMPANY'""",
                    rs -> { cnpjByAsset.put(rs.getLong(1), rs.getString(2)); });
        }
        for (Asset a : catalog) {
            if (!b3(a) || manual.contains(a.getId())) {
                continue;
            }
            CvmCancellation c = cancelled.get(cnpjByAsset.get(a.getId()));
            if (c == null || !c.leftTheExchange()) {
                continue;
            }
            LocalDate last = last(a, lastTrade, idByTicker);
            LocalDate out = last != null ? last : c.date();
            if (inactivate(a, out, DelistingSource.CVM)) {
                byCvm++;
                notify(a, out, held, "Registro da companhia cancelado na CVM"
                        + (c.date() != null ? " em " + c.date().format(BR) : "")
                        + (c.reason() != null ? " (" + c.reason() + ")" : "") + ".");
            }
        }

        // 2. Fora da lista oficial e sem negocio ha 30 pregoes, sem ser renomeacao
        int byListGone = 0;
        LocalDate cutoff = cutoff(today);
        if (official.isEmpty() || renamedOld == null || cutoff == null) {
            log.warn("Conferencia do catalogo: inativacao por sumico nao rodou (lista oficial, renomes ou COTAHIST "
                    + "indisponiveis)");
        } else {
            for (Asset a : catalog) {
                if (!b3(a) || manual.contains(a.getId()) || !a.isActive()) {
                    continue;
                }
                String ticker = a.getTicker().toUpperCase();
                String base = base(ticker);
                if (official.containsKey(ticker) || official.containsKey(base) || ignored.contains(ticker)
                        || renamedOld.contains(ticker) || renamedOld.contains(base)) {
                    continue;
                }
                LocalDate last = last(a, lastTrade, idByTicker);
                // Sem pregao nenhum no COTAHIST nao ha data de saida a provar: fica so no aviso de sumido
                if (last == null || !last.isBefore(cutoff)) {
                    continue;
                }
                if (inactivate(a, last, DelistingSource.LIST_GONE)) {
                    byListGone++;
                    notify(a, last, held, "Fora da lista oficial da B3 (BRAPI) e sem negócio no COTAHIST desde "
                            + last.format(BR) + " (" + SESSIONS + " pregões ou mais), sem renomeação.");
                }
            }
        }

        // 3. Data de saida dos inativos que ainda nao tem (I5, 1a parte)
        int filled = fillDates(lastTrade);
        log.info("Conferencia do catalogo: {} inativados pela CVM, {} por sumico; {} datas de saida preenchidas",
                byCvm, byListGone, filled);
        return new Result(byCvm, byListGone, filled);
    }

    /** Papel da B3 (as regras do exterior vêm com a F4). */
    private static boolean b3(Asset a) {
        return a.getAssetType() != null && AssetTypeNames.B3_LISTED.contains(a.getAssetType().getName());
    }

    /** Fracionário (PETR4F) → o papel inteiro (PETR4). */
    static String base(String ticker) {
        return ticker.endsWith("F") && ticker.length() > 5 ? ticker.substring(0, ticker.length() - 1) : ticker;
    }

    /** Último pregão com negócio do papel; no fracionário, o mais recente entre ele e o papel inteiro. */
    private static LocalDate last(Asset a, Map<Long, LocalDate> lastTrade, Map<String, Long> idByTicker) {
        LocalDate own = lastTrade.get(a.getId());
        String ticker = a.getTicker().toUpperCase();
        String base = base(ticker);
        LocalDate whole = base.equals(ticker) ? null : lastTrade.get(idByTicker.get(base));
        return own == null ? whole : whole == null || own.isAfter(whole) ? own : whole;
    }

    private Map<Long, LocalDate> lastTrades() {
        Map<Long, LocalDate> out = new HashMap<>();
        jdbc.query("""
                SELECT asset_id, MAX(reference_date) FROM asset_price_history
                 WHERE source = 'B3_COTAHIST' GROUP BY asset_id""",
                rs -> { out.put(rs.getLong(1), rs.getDate(2).toLocalDate()); });
        return out;
    }

    /**
     * O {@value #SESSIONS}º pregão mais recente do COTAHIST: negócio antes dele = 30 pregões sem negócio. Nulo quando o
     * COTAHIST está velho (o diário parou) ou curto demais — sem prova, nada é inativado.
     */
    private LocalDate cutoff(LocalDate today) {
        List<java.sql.Date> sessions = jdbc.queryForList("""
                SELECT DISTINCT reference_date FROM asset_price_history WHERE source = 'B3_COTAHIST'
                 ORDER BY reference_date DESC LIMIT ?""", java.sql.Date.class, SESSIONS);
        if (sessions.size() < SESSIONS || sessions.get(0).toLocalDate().isBefore(today.minusDays(MAX_COTAHIST_AGE_DAYS))) {
            return null;
        }
        return sessions.get(SESSIONS - 1).toLocalDate();
    }

    private Map<String, CvmCancellation> cancellations() {
        try (InputStream in = Files.newInputStream(cvm.companyRegistry())) {
            return CvmCancellation.parse(in);
        } catch (Exception e) {
            log.warn("Conferencia do catalogo: cadastro da CVM indisponivel, inativacao pela CVM nao rodou: {}",
                    e.getMessage());
            notices.sourceUnavailable("Conferência do catálogo", "CVM", e.getMessage());
            return Map.of();
        }
    }

    /** Inativa com a prova; nunca por cima da decisão do administrador. */
    private boolean inactivate(Asset a, LocalDate out, DelistingSource source) {
        int n = jdbc.update("""
                UPDATE assets SET active = false, delisted_on = ?, delisting_source = ?
                 WHERE id = ? AND active AND (delisting_source IS NULL OR delisting_source <> 'MANUAL')""",
                out == null ? null : java.sql.Date.valueOf(out), source.name(), a.getId());
        if (n > 0) {
            log.warn("Catalogo: {} inativado ({}), saida em {}", a.getTicker(), source, out);
        }
        return n > 0;
    }

    private int fillDates(Map<Long, LocalDate> lastTrade) {
        Map<String, Long> ids = new HashMap<>();
        Map<Long, String> tickers = new HashMap<>();
        jdbc.query("""
                SELECT a.id, a.ticker FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                 WHERE t.name IN ('ACAO', 'FII', 'ETF', 'BDR')""",
                rs -> {
                    ids.put(rs.getString(2).toUpperCase(), rs.getLong(1));
                    tickers.put(rs.getLong(1), rs.getString(2).toUpperCase());
                });
        int filled = 0;
        for (Long id : jdbc.queryForList("""
                SELECT a.id FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                 WHERE NOT a.active AND a.delisted_on IS NULL AND a.delisting_source IS NULL
                   AND t.name IN ('ACAO', 'FII', 'ETF', 'BDR')""", Long.class)) {
            String ticker = tickers.get(id);
            LocalDate own = lastTrade.get(id);
            LocalDate whole = base(ticker).equals(ticker) ? null : lastTrade.get(ids.get(base(ticker)));
            LocalDate last = own == null ? whole : whole == null || own.isAfter(whole) ? own : whole;
            if (last != null) {
                filled += jdbc.update("UPDATE assets SET delisted_on = ? WHERE id = ? "
                        + "AND delisted_on IS NULL", java.sql.Date.valueOf(last), id);
            }
        }
        return filled;
    }

    private void notify(Asset a, LocalDate out, Set<Long> held, String proof) {
        String ticker = a.getTicker().toUpperCase();
        notices.assetPending(NotificationKind.CATALOGO_TICKER_INATIVADO, a, key(a.getId()),
                ticker + " inativado — saiu da bolsa" + (out != null ? " em " + out.format(BR) : "")
                        + (held.contains(a.getId()) ? " (há posição aberta)" : ""),
                "Prova: " + proof + " O papel some da busca (só a Nova transação o traz, para lançamento até a data "
                        + "de saída). Se a inativação estiver errada, desfaça pela curadoria: "
                        + "PUT /api/v1/admin/assets/" + a.getId() + "/activation com {\"active\": true}.");
    }

    /** Chave do aviso de inativação: o id do ativo (a curadoria resolve o aviso ao desfazer). */
    public static String key(Long assetId) {
        return "INATIVO:" + assetId;
    }
}

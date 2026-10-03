package com.primevest.adm.integration.cvm;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Informe mensal de FII, partes {@code complemento} (PL, cotas, cotistas, taxas) e
 * {@code ativo_passivo} (carteira e passivo) — docs/planos/PLANO_EMPRESAS_BALANCOS.md, 1.2 e F7.
 *
 * <p>Uma linha por fundo e mes; reapresentacao vem como outra linha com {@code Versao}
 * maior, e fica a maior de cada arquivo. As duas partes se juntam por CNPJ e mes; a
 * versao do mes e a maior das duas. Colunas mudam de nome entre anos
 * ({@code CNPJ_Fundo} ate 2021, {@code CNPJ_Fundo_Classe} depois; {@code CRI_CRA} e
 * {@code LCI_LCA} de 2019 em diante, ao lado de {@code CRI} e {@code LCI}).</p>
 */
public record CvmFiiMonthly(String cnpj, LocalDate referenceMonth, int version,
                            BigDecimal netAssets, BigDecimal totalAssets, BigDecimal sharesIssued,
                            BigDecimal navPerShare, Integer shareholders,
                            BigDecimal adminFeeMonth, BigDecimal dividendYieldMonth, BigDecimal amortizationMonth,
                            BigDecimal liquidityTotal, BigDecimal totalInvested, BigDecimal realEstateTotal,
                            BigDecimal realEstateCompanies, BigDecimal receivablesTotal, BigDecimal fiiSharesTotal,
                            BigDecimal liabilitiesTotal, BigDecimal incomeToDistribute) {

    public record Key(String cnpj, LocalDate referenceMonth) {
    }

    private record Complement(int version, BigDecimal netAssets, BigDecimal totalAssets, BigDecimal sharesIssued,
                              BigDecimal navPerShare, Integer shareholders, BigDecimal adminFee,
                              BigDecimal dividendYield, BigDecimal amortization) {
    }

    private record Portfolio(int version, BigDecimal liquidity, BigDecimal invested, BigDecimal realEstate,
                             BigDecimal realEstateCompanies, BigDecimal receivables, BigDecimal fiiShares,
                             BigDecimal liabilities, BigDecimal incomeToDistribute) {
    }

    /**
     * Meses do zip de um ano, so dos CNPJs aceitos por {@code wanted} (14 digitos).
     * Mes sem nenhuma das duas partes nao aparece.
     */
    public static Map<Key, CvmFiiMonthly> read(Path zip, int year, Predicate<String> wanted) {
        Map<Key, Complement> complements = new HashMap<>();
        CvmZip.forEach(zip, "inf_mensal_fii_complemento_" + year + ".csv", row -> {
            Key key = key(row, wanted);
            if (key == null) {
                return;
            }
            Complement c = new Complement(CvmFca.integer(row.get("Versao"), 1),
                    decimal(row.get("Patrimonio_Liquido")), decimal(row.get("Valor_Ativo")),
                    decimal(row.get("Cotas_Emitidas")), decimal(row.get("Valor_Patrimonial_Cotas")),
                    integer(row.get("Total_Numero_Cotistas")),
                    decimal(row.get("Percentual_Despesas_Taxa_Administracao")),
                    decimal(row.get("Percentual_Dividend_Yield_Mes")),
                    decimal(row.get("Percentual_Amortizacao_Cotas_Mes")));
            complements.merge(key, c, (a, b) -> b.version() > a.version() ? b : a);
        });
        Map<Key, Portfolio> portfolios = new HashMap<>();
        CvmZip.forEach(zip, "inf_mensal_fii_ativo_passivo_" + year + ".csv", row -> {
            Key key = key(row, wanted);
            if (key == null) {
                return;
            }
            Portfolio p = new Portfolio(CvmFca.integer(row.get("Versao"), 1),
                    decimal(row.get("Total_Necessidades_Liquidez")), decimal(row.get("Total_Investido")),
                    decimal(row.get("Direitos_Bens_Imoveis")),
                    sum(row, "Acoes_Sociedades_Atividades_FII", "Cotas_Sociedades_Atividades_FII"),
                    sum(row, "CRI", "CRI_CRA", "Letras_Hipotecarias", "LCI", "LCI_LCA", "LIG"),
                    decimal(row.get("FII")), decimal(row.get("Total_Passivo")),
                    decimal(row.get("Rendimentos_Distribuir")));
            portfolios.merge(key, p, (a, b) -> b.version() > a.version() ? b : a);
        });

        Map<Key, CvmFiiMonthly> out = new HashMap<>();
        for (Key key : union(complements, portfolios)) {
            Complement c = complements.get(key);
            Portfolio p = portfolios.get(key);
            int version = Math.max(c == null ? 0 : c.version(), p == null ? 0 : p.version());
            out.put(key, new CvmFiiMonthly(key.cnpj(), key.referenceMonth(), version,
                    c == null ? null : c.netAssets(), c == null ? null : c.totalAssets(),
                    c == null ? null : c.sharesIssued(), c == null ? null : c.navPerShare(),
                    c == null ? null : c.shareholders(), c == null ? null : c.adminFee(),
                    c == null ? null : c.dividendYield(), c == null ? null : c.amortization(),
                    p == null ? null : p.liquidity(), p == null ? null : p.invested(),
                    p == null ? null : p.realEstate(), p == null ? null : p.realEstateCompanies(),
                    p == null ? null : p.receivables(), p == null ? null : p.fiiShares(),
                    p == null ? null : p.liabilities(), p == null ? null : p.incomeToDistribute()));
        }
        return out;
    }

    private static java.util.Set<Key> union(Map<Key, ?> a, Map<Key, ?> b) {
        java.util.Set<Key> keys = new java.util.HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        return keys;
    }

    private static Key key(CvmCsv.Row row, Predicate<String> wanted) {
        String cnpj = CvmCsv.cnpj(row.first("CNPJ_Fundo_Classe", "CNPJ_Fundo"));
        if (cnpj == null || !wanted.test(cnpj)) {
            return null;
        }
        LocalDate ref = CvmFca.date(row.get("Data_Referencia"));
        return ref == null ? null : new Key(cnpj, ref.withDayOfMonth(1));
    }

    /** Soma das colunas presentes; nulo se nenhuma tiver valor. */
    private static BigDecimal sum(CvmCsv.Row row, String... columns) {
        BigDecimal total = null;
        for (String c : columns) {
            BigDecimal v = decimal(row.get(c));
            if (v != null) {
                total = total == null ? v : total.add(v);
            }
        }
        return total;
    }

    static BigDecimal decimal(String s) {
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(String s) {
        BigDecimal d = decimal(s);
        if (d == null) {
            return null;
        }
        try {
            return d.intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }
}

package com.activest.adm.integration.b3;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Os ~12 meses de proventos da B3 guardados em disco, acumulando
 * (docs/planos/PLANO_CARGA_INICIAL.md, 5.2 — P1).
 *
 * <p>A resposta da B3 ({@code GetListedSupplementCompany} / {@code GetListedSupplementFunds}) e a
 * unica fonte com <b>data de pagamento</b> para acao, mas so cobre ~12 meses: o evento que sai da
 * janela perde a data em qualquer banco novo. Aqui cada resposta e somada ao arquivo
 * {@code carga/data/b3/proventos-12m/{acoes|fundos}/{EMISSOR}.json} (o mesmo formato da resposta): o
 * que a B3 devolve agora substitui a mesma linha do arquivo, e o que ja saiu da janela fica. O
 * arquivo e o estado (sem tabela nem coluna de controle) e vai para o bucket com o resto da
 * {@code carga/data/}.</p>
 *
 * <p><b>Mesma linha</b> = mesmo ISIN, rotulo, data-com, valor e aprovacao — sem a data de
 * pagamento, para que uma correcao da B3 na data substitua a anterior em vez de virar parcela. As
 * parcelas (linhas iguais que so mudam no pagamento) chegam juntas na resposta e entram juntas.</p>
 */
@Slf4j
@Component
public class B3SupplementStore {

    private final ObjectMapper objectMapper;
    private final Path dir;

    @Autowired
    public B3SupplementStore(ObjectMapper objectMapper, @Value("${activest.data.dir:../carga/data}") String dataDir) {
        this(objectMapper, Path.of(dataDir, "b3", "proventos-12m"));
    }

    B3SupplementStore(ObjectMapper objectMapper, Path dir) {
        this.objectMapper = objectMapper;
        this.dir = dir.toAbsolutePath().normalize();
    }

    /** O que esta em disco para o emissor; nulo sem arquivo (ou ilegivel). */
    public B3Supplement read(boolean fund, String issuer) {
        Path file = file(fund, issuer);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), B3Supplement.class);
        } catch (IOException e) {
            log.warn("Proventos: {} ilegivel ({}) — sera regravado com a proxima resposta da B3", file, e.getMessage());
            return null;
        }
    }

    /**
     * Soma a resposta da B3 ao arquivo e devolve o resultado (o que a rotina aplica). Sem
     * resposta ({@code fresh} nulo: a B3 nao achou o emissor ou caiu), devolve o que esta em disco.
     */
    public B3Supplement merge(boolean fund, String issuer, B3Supplement fresh) {
        B3Supplement old = read(fund, issuer);
        if (fresh == null) {
            return old;
        }
        B3Supplement merged = merge(old, fresh);
        if (!merged.equals(old)) {
            write(file(fund, issuer), merged);
        }
        return merged;
    }

    static B3Supplement merge(B3Supplement old, B3Supplement fresh) {
        if (old == null) {
            old = new B3Supplement(null, null, null, List.of(), List.of());
        }
        List<B3Supplement.CashDividend> cash = union(old.cash(), fresh.cash(), B3SupplementStore::cashKey);
        cash.sort(Comparator.comparing((B3Supplement.CashDividend c) -> B3Values.date(c.lastDatePrior()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> Objects.toString(c.isinCode(), ""))
                .thenComparing(c -> Objects.toString(c.label(), "")));
        List<B3Supplement.StockDividend> stock = union(old.stock(), fresh.stock(), B3SupplementStore::stockKey);
        stock.sort(Comparator.comparing((B3Supplement.StockDividend s) -> B3Values.date(s.lastDatePrior()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> Objects.toString(s.isinCode(), "")));
        return new B3Supplement(
                fresh.tradingName() != null ? fresh.tradingName() : old.tradingName(),
                fresh.code() != null ? fresh.code() : old.code(),
                fresh.codeCVM() != null ? fresh.codeCVM() : old.codeCVM(),
                cash, stock);
    }

    /** O antigo cuja chave nao voltou na resposta, mais toda a resposta (em ordem, com as parcelas). */
    private static <T> List<T> union(List<T> old, List<T> fresh, Function<T, String> key) {
        Set<String> freshKeys = new LinkedHashSet<>();
        fresh.forEach(f -> freshKeys.add(key.apply(f)));
        List<T> out = new ArrayList<>();
        for (T o : old) {
            if (!freshKeys.contains(key.apply(o))) {
                out.add(o);
            }
        }
        out.addAll(fresh);
        return out;
    }

    static String cashKey(B3Supplement.CashDividend c) {
        return String.join("|", Objects.toString(B3Values.trim(c.isinCode()), ""), Objects.toString(B3Values.trim(c.label()), ""),
                date(c.lastDatePrior()), number(c.rate()), date(c.approvedOn()));
    }

    static String stockKey(B3Supplement.StockDividend s) {
        return String.join("|", Objects.toString(B3Values.trim(s.isinCode()), ""), Objects.toString(B3Values.trim(s.label()), ""),
                date(s.lastDatePrior()), number(s.factor()));
    }

    private static String date(String text) {
        LocalDate d = B3Values.date(text);
        return d == null ? "" : d.toString();
    }

    private static String number(String text) {
        BigDecimal v = B3Values.decimal(text);
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private Path file(boolean fund, String issuer) {
        return dir.resolve(fund ? "fundos" : "acoes")
                .resolve(issuer.toUpperCase().replaceAll("[^A-Z0-9]", "_") + ".json");
    }

    private void write(Path file, B3Supplement merged) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), merged);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // sem o arquivo, a proxima resposta tenta de novo; o que ja saiu da janela so
            // existe aqui, entao o aviso e de nivel WARN
            log.warn("Proventos: {} nao gravado: {}", file, e.getMessage());
        }
    }
}

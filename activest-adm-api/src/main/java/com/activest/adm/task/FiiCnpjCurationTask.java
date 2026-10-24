package com.activest.adm.task;

import com.activest.adm.domain.issuer.IssuerLinkService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Curadoria: CNPJ dos FIIs (D4). {@code LINES}: uma linha {@code TICKER;CNPJ} por fundo. O resultado de cada linha vai
 * para o log e para quem chamou de forma síncrona (o endpoint {@code /issuers/fii-cnpjs}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FiiCnpjCurationTask implements TaskHandler {

    private final IssuerLinkService service;

    @Override
    public String type() {
        return "FII_CNPJ_CURATION";
    }

    @Override
    public void run(TaskRunContext ctx) {
        List<IssuerLinkService.FundCnpj> lines = parse(ctx.requireText("LINES"));
        List<IssuerLinkService.CuratedLine> result = service.applyCuratedFunds(lines);
        result.forEach(l -> log.info("Curadoria FII: {} {} -> {} {}", l.ticker(), l.cnpj(), l.status(),
            l.issuer() == null ? "" : l.issuer()));
        Map<String, Long> byStatus = result.stream()
            .collect(Collectors.groupingBy(IssuerLinkService.CuratedLine::status, Collectors.counting()));
        ctx.summary(result.size() + " linha(s): " + byStatus);
        ctx.result(result);
    }

    /** {@code TICKER;CNPJ} por linha; linha vazia é ignorada. */
    public static List<IssuerLinkService.FundCnpj> parse(String text) {
        List<IssuerLinkService.FundCnpj> lines = new ArrayList<>();
        int n = 0;
        for (String raw : text.split("\\R")) {
            n++;
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(";");
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new TaskInputException("LINES, linha " + n + ": use TICKER;CNPJ.");
            }
            lines.add(new IssuerLinkService.FundCnpj(parts[0].strip().toUpperCase(), parts[1].strip()));
        }
        if (lines.isEmpty()) {
            throw new TaskInputException("LINES: nenhuma linha TICKER;CNPJ.");
        }
        return lines;
    }

    /** O inverso de {@link #parse}: o corpo do endpoint de hoje vira o atributo. */
    public static String format(List<IssuerLinkService.FundCnpj> lines) {
        return lines.stream().map(l -> l.ticker() + ";" + l.cnpj()).collect(Collectors.joining("\n"));
    }
}

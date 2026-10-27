package com.activest.adm.task;

import com.activest.adm.domain.currency.ExchangeRateImportService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Câmbio das moedas cadastradas (docs/planos/PLANO_CADASTRO_MOEDAS.md, 10.3): sem atributos, a rodada incremental;
 * com moeda ou data, a carga do período (D28). <b>No máximo uma rodada automática por dia</b> (P2): agendada às 13:30,
 * a subida só substitui a agendada perdida, o Run manual roda sempre.
 */
@Component
@RequiredArgsConstructor
public class ExchangeRatesTask implements TaskHandler {

    private final ExchangeRateImportService service;

    @Override
    public String type() {
        return "EXCHANGE_RATES";
    }

    @Override
    public void run(TaskRunContext ctx) {
        List<String> codes = ctx.list("CURRENCY_CODES").stream().map(String::toUpperCase).toList();
        LocalDate from = ctx.date("FROM_DATE");
        if (!codes.isEmpty()) {
            try {
                service.validateCodes(codes);
            } catch (IllegalArgumentException e) {
                throw new TaskInputException(e.getMessage());
            }
        }
        if (from != null && (from.isBefore(ExchangeRateImportService.HISTORY_FROM) || from.isAfter(LocalDate.now()))) {
            throw new TaskInputException("Data inicial inválida: " + from + " (de "
                + ExchangeRateImportService.HISTORY_FROM + " até hoje).");
        }
        var result = (from == null && codes.isEmpty())
            ? service.importRecent(ctx.reason())
            : service.importFrom(codes, from, ctx.reason());
        if (result == null) {
            throw new TaskSkippedException("O câmbio já estava sendo carregado por outro caminho.");
        }
        ctx.summary(result.toString());
        if (!result.failures().isEmpty()) {
            throw new IllegalStateException("Câmbio indisponível para " + String.join(", ", result.failures())
                + " (detalhes no log da rodada).");
        }
    }

    @Override
    public boolean needsRecovery() {
        return service.behind();
    }

    @Override
    public boolean oncePerDay() {
        return true;
    }

    @Override
    public boolean stoppable() {
        return true;
    }
}

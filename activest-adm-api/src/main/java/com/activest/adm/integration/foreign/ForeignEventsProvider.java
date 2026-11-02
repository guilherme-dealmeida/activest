package com.activest.adm.integration.foreign;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Fonte de eventos dos ativos do exterior (docs/planos/PLANO_PROVENTOS_INTERNACIONAIS.md, D1 e D9): proventos e
 * desdobramentos de um mês inteiro do mercado, com o ticker já na grafia do catálogo. A primeira implementação é o
 * Massive ({@code /v3/reference/dividends} e {@code /v3/reference/splits}).
 */
public interface ForeignEventsProvider {

    /** Nome da fonte para os logs e as mensagens. */
    String sourceName();

    /**
     * Proventos com ex-date no mês.
     *
     * @param refresh pergunta de novo mesmo com o mês em disco
     */
    Month<Dividend> dividends(YearMonth month, boolean refresh);

    /** Desdobramentos/grupamentos com data de execução (a ex-date) no mês. */
    Month<Split> splits(YearMonth month, boolean refresh);

    /** Os eventos do mês e se vieram do arquivo guardado. */
    record Month<T>(YearMonth month, List<T> events, boolean fromDisk, int pages) {
    }

    /**
     * Um provento. {@code type}: o tipo da fonte (no Massive, CD normal, SC especial, LT/ST ganho de capital de fundo).
     */
    record Dividend(String id, String ticker, LocalDate exDate, LocalDate payDate, LocalDate recordDate,
                    LocalDate declarationDate, BigDecimal amount, String currency, String type) {
    }

    /** Um desdobramento: {@code from} cotas viram {@code to} na ex-date. */
    record Split(String id, String ticker, LocalDate exDate, BigDecimal from, BigDecimal to) {
    }
}

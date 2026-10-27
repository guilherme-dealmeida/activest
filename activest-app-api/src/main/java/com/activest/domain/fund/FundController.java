package com.activest.domain.fund;

import com.activest.config.SecurityUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** Fundos de investimento (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, 11.5). */
@RestController
@RequestMapping("/api/v1/funds")
public class FundController {

    private final FundQuoteService quotes;

    public FundController(FundQuoteService quotes) {
        this.quotes = quotes;
    }

    /** Cota do fundo na data (ou a última anterior, com a data): pré-preenche o Preço da Nova transação (C6). */
    @GetMapping("/{ticker}/quote")
    public FundQuoteService.Quote quote(@PathVariable String ticker,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return quotes.quote(ticker, date);
    }

    /** Ticker → nome curto dos fundos que o usuário já lançou (o rótulo das telas, D3). */
    @GetMapping("/labels")
    public Map<String, String> labels() {
        return quotes.labels(SecurityUtils.getCurrentUserId());
    }
}

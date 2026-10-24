package com.activest.domain.marketindex;

import com.activest.domain.marketindex.MarketIndexDTOs.AccumulatedDTO;
import com.activest.domain.marketindex.MarketIndexDTOs.ChartDTO;
import com.activest.domain.marketindex.MarketIndexDTOs.HistoryDTO;
import com.activest.domain.marketindex.MarketIndexDTOs.IndexDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Indices de mercado (docs/planos/PLANO_INDICES_MERCADO.md, X5): lista, serie e acumulado. Autenticado, so
 * leitura do catalogo.
 */
@RestController
@RequestMapping("/api/v1/indices")
public class MarketIndexController {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final MarketIndexQueryService service;

    public MarketIndexController(MarketIndexQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<IndexDTO>> list() {
        return ResponseEntity.ok(service.list());
    }

    /** Serie de {@code from} (padrao: um ano antes de {@code to}) a {@code to} (padrao: hoje). */
    @GetMapping("/{code}/history")
    public ResponseEntity<HistoryDTO> history(@PathVariable String code,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZONE);
        return ResponseEntity.ok(service.history(code, from != null ? from : end.minusYears(1), end));
    }

    /**
     * Grafico de um card da tela Indices: {@code range} (1D, 7D, 1M, 6M, YTD, 1A, 5A, MAX; padrao 1A) ou o periodo
     * especifico {@code from}/{@code to}; {@code view} (so nos mensais) MENSAL ou ANUAL — acumulado em 12 meses.
     * Valor exibido, resumo e densidade em {@link IndexChart}.
     */
    @GetMapping("/{code}/chart")
    public ResponseEntity<ChartDTO> chart(@PathVariable String code,
                                          @RequestParam(required = false) String range,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) String view) {
        return ResponseEntity.ok(service.chart(code, range, from, to, view));
    }

    /**
     * Acumulado de {@code from} a {@code to} (padrao: hoje). {@code percent} = percentual do indice (110 = 110% do
     * CDI); {@code spread} = spread anual em % (6 = IPCA + 6% a.a.). Os dois so em indice de taxa.
     */
    @GetMapping("/{code}/accumulated")
    public ResponseEntity<AccumulatedDTO> accumulated(@PathVariable String code,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                      LocalDate from,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(required = false) BigDecimal percent,
                                                      @RequestParam(required = false) BigDecimal spread) {
        return ResponseEntity.ok(service.accumulated(code, from, to != null ? to : LocalDate.now(ZONE), percent,
                spread));
    }
}

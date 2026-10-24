package com.activest.domain.indicator;

import com.activest.domain.indicator.dto.AssetIndicatorsDTO;
import com.activest.domain.indicator.dto.IndicatorHistoryDTO;
import com.activest.domain.indicator.dto.IssuerListingRowDTO;
import com.activest.domain.issuer.IssuerKind;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Indicadores de acoes e FIIs (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.4 — F9). Leitura do catalogo,
 * para qualquer usuario autenticado.
 */
@RestController
@RequestMapping("/api/v1/assets")
public class IndicatorController {

    private final IndicatorService service;
    private final IssuerListingService listing;
    private final CompanyDataService companyData;

    public IndicatorController(IndicatorService service, IssuerListingService listing, CompanyDataService companyData) {
        this.service = service;
        this.listing = listing;
        this.companyData = companyData;
    }

    /**
     * Card "Dados da empresa" / "Dados do fundo" (docs/planos/PLANO_TELA_ATIVOS.md, D2 a D7): valor de mercado, de
     * firma, PL, papeis, ativos, circulante, dividas, caixa, liquidez media e as datas.
     */
    @GetMapping("/{ticker}/company-data")
    public ResponseEntity<com.activest.domain.indicator.dto.CompanyDataDTO> companyData(@PathVariable String ticker) {
        return ResponseEntity.ok(companyData.of(ticker));
    }

    /**
     * Listagem de emissoras: o "Atual" de todos os tickers de uma aba ({@code kind=COMPANY} ou {@code FII}), ja na
     * ordem padrao (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 4.2).
     */
    @GetMapping("/indicators")
    public ResponseEntity<List<IssuerListingRowDTO>> listing(@RequestParam String kind) {
        return ResponseEntity.ok(listing.list(parse(IssuerKind.class, kind, "Tipo de emissor")));
    }

    /** Indicadores atuais: ultimo balanco ou informe com o preco atual. */
    @GetMapping("/{ticker}/indicators")
    public ResponseEntity<AssetIndicatorsDTO> current(@PathVariable String ticker) {
        return ResponseEntity.ok(service.current(ticker));
    }

    /** Historico de todos os indicadores do ticker numa chamada (sparklines da tela de emissoras, B4). */
    @GetMapping("/{ticker}/indicators/history")
    public ResponseEntity<List<IndicatorHistoryDTO>> historyAll(@PathVariable String ticker,
                                                                @RequestParam(defaultValue = "YEAR") String period) {
        return ResponseEntity.ok(service.historyAll(ticker, parse(IndicatorService.Period.class, period, "Periodo")));
    }

    /** Historico de um indicador: pontos anuais ({@code period=YEAR}, padrao) ou trimestrais, Atual e media. */
    @GetMapping("/{ticker}/indicators/{indicator}/history")
    public ResponseEntity<IndicatorHistoryDTO> history(@PathVariable String ticker, @PathVariable String indicator,
                                                       @RequestParam(defaultValue = "YEAR") String period) {
        return ResponseEntity.ok(service.history(ticker, parse(Indicator.class, indicator, "Indicador"),
                parse(IndicatorService.Period.class, period, "Periodo")));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String what) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, what + " invalido: " + value);
        }
    }
}

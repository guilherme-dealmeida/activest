package com.primevest.domain.issuer;

import com.primevest.domain.issuer.dto.IssuerDTOs.IssuerDTO;
import com.primevest.domain.issuer.dto.IssuerDTOs.MonthDTO;
import com.primevest.domain.issuer.dto.IssuerDTOs.QuarterDTO;
import com.primevest.domain.issuer.dto.IssuerDTOs.Ticker;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Emissor para a tela (PLANO_EMPRESAS_BALANCOS.md, 5.4 — F9): cadastro, tickers ligados,
 * serie trimestral (companhia) e informe mensal (FII). So leitura do catalogo.
 */
@RestController
@RequestMapping("/api/v1/issuers")
public class IssuerController {

    private static final LocalDate FIRST = LocalDate.of(2000, 1, 1);

    private final IssuerRepository issuers;
    private final CompanyQuarterRepository quarters;
    private final FiiMonthlyRepository months;
    private final JdbcTemplate jdbc;

    public IssuerController(IssuerRepository issuers, CompanyQuarterRepository quarters, FiiMonthlyRepository months,
                            JdbcTemplate jdbc) {
        this.issuers = issuers;
        this.quarters = quarters;
        this.months = months;
        this.jdbc = jdbc;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<IssuerDTO> get(@PathVariable Long id) {
        Issuer issuer = find(id);
        List<Ticker> tickers = jdbc.query(
                "SELECT ticker, share_class, active FROM assets WHERE issuer_id = ? ORDER BY ticker",
                (rs, i) -> new Ticker(rs.getString("ticker"), rs.getString("share_class"), rs.getBoolean("active")),
                id);
        return ResponseEntity.ok(IssuerDTO.of(issuer, tickers));
    }

    /** Serie trimestral da companhia, do mais antigo ao mais recente. */
    @GetMapping("/{id}/quarters")
    public ResponseEntity<List<QuarterDTO>> quarters(@PathVariable Long id,
                                                     @RequestParam(required = false)
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        Issuer issuer = find(id);
        if (issuer.getKind() != IssuerKind.COMPANY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serie trimestral so de companhia");
        }
        return ResponseEntity.ok(quarters.findByIssuerIdAndPeriodEndGreaterThanEqualOrderByPeriodEnd(id,
                from != null ? from : FIRST).stream().map(QuarterDTO::of).toList());
    }

    /** Informe mensal do FII, do mais antigo ao mais recente. */
    @GetMapping("/{id}/months")
    public ResponseEntity<List<MonthDTO>> months(@PathVariable Long id,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        Issuer issuer = find(id);
        if (issuer.getKind() != IssuerKind.FII) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe mensal so de FII");
        }
        return ResponseEntity.ok(months.findByIssuerIdAndReferenceMonthGreaterThanEqualOrderByReferenceMonth(id,
                from != null ? from : FIRST).stream().map(MonthDTO::of).toList());
    }

    private Issuer find(Long id) {
        return issuers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emissor nao encontrado: " + id));
    }
}

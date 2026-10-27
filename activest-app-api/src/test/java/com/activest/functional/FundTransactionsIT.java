package com.activest.functional;

import com.activest.domain.transaction.dto.TransactionResponseDTO;
import com.activest.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transação e posição de fundo de investimento (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, F6: D16, D24, C6, C7, C10).
 * O catálogo de teste não tem fundo (vem da CVM em tempo de execução): o cenário cria um, sem série de cotas — sem
 * come-cotas (F7 tem os seus testes).
 */
class FundTransactionsIT extends FunctionalBase {

    static final String FUND = "99999999000191";

    private User owner;

    @BeforeEach
    void scenario() {
        createFund(jdbc, FUND, "Multimercado", true);
        owner = userWithPortfolio("fundo-" + System.nanoTime() + "@activest.test");
    }

    /** Um fundo no catálogo: ativo do tipo FUNDO_INVESTIMENTO e o cadastro CVM (o que a tarefa FUND_QUOTES grava). */
    static Long createFund(org.springframework.jdbc.core.JdbcTemplate jdbc, String cnpj, String category,
                           boolean longTermTax) {
        java.util.List<Long> ids = jdbc.queryForList("SELECT id FROM assets WHERE ticker = ?", Long.class, cnpj);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        Long id = jdbc.queryForObject("""
            INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, currency_code, current_price, active)
            SELECT ?, 'FUNDO DE TESTE ' || ?, t.id, c.id, 'BRL', 5, true
              FROM asset_types t JOIN asset_categories c ON c.asset_type_id = t.id AND c.name = ?
             WHERE t.name = 'FUNDO_INVESTIMENTO'
            RETURNING id""", Long.class, cnpj, cnpj, category);
        jdbc.update("""
            INSERT INTO investment_funds (asset_id, cnpj, short_name, fund_kind, long_term_tax, search_text)
            VALUES (?, ?, ?, 'FIF', ?, ?)""", id, cnpj, "FUNDO TESTE " + cnpj.substring(0, 4), longTermTax, cnpj);
        return id;
    }

    /** Aplicação ou resgate de fundo: valor e cota (D16, D24); as cotas saem da divisão. */
    static Map<String, Object> fundEntry(String ticker, String type, String date, String value, String quote) {
        Map<String, Object> body = new HashMap<>();
        body.put("ticker", ticker);
        body.put("type", type);
        body.put("transactionDate", date);
        body.put("totalAmount", new BigDecimal(value));
        body.put("pricePerShare", new BigDecimal(quote));
        return body;
    }

    @Test
    @DisplayName("C6: aplicação pelo valor e pela cota — cotas = valor ÷ cota, 8 casas; quantidade enviada é ignorada")
    void buyByValue() {
        Map<String, Object> body = fundEntry(FUND, "BUY", "2026-01-05", "1000.00", "3");
        body.put("shares", new BigDecimal("1"));
        TransactionResponseDTO r = recordSuccessfully(owner, body);

        assertThat(r.shares()).isEqualByComparingTo("333.33333333");
        assertThat(r.pricePerShare()).isEqualByComparingTo("3");
        assertThat(r.totalAmount()).isEqualByComparingTo("1000.00");

        Map<String, Object> position = positionOf(owner, FUND);
        assertThat(number(position.get("totalShares"))).isEqualByComparingTo("333.33333333");
        assertThat(number(position.get("totalInvested"))).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("C6: sem valor ou sem cota, ou com valor zerado, a aplicação é recusada")
    void buyNeedsValueAndQuote() {
        Map<String, Object> noQuote = fundEntry(FUND, "BUY", "2026-01-05", "1000.00", "3");
        noQuote.remove("pricePerShare");
        assertThat(recordExpectingError(owner, noQuote).getStatusCode().is4xxClientError()).isTrue();
        ResponseEntity<String> zero = recordExpectingError(owner, fundEntry(FUND, "BUY", "2026-01-05", "0", "3"));
        assertThat(zero.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("C7/C10: resgate pelo valor bruto tira as cotas da posição; acima do saldo é bloqueado")
    void redemption() {
        recordSuccessfully(owner, fundEntry(FUND, "BUY", "2026-01-05", "1000.00", "2.5"));   // 400 cotas
        recordSuccessfully(owner, fundEntry(FUND, "BUY", "2026-03-02", "1000.00", "4"));     // 250 cotas
        TransactionResponseDTO sale = recordSuccessfully(owner, fundEntry(FUND, "SELL", "2026-04-01", "1500.00", "5"));
        assertThat(sale.shares()).isEqualByComparingTo("300");
        assertThat(number(positionOf(owner, FUND).get("totalShares"))).isEqualByComparingTo("350");

        ResponseEntity<String> tooMuch = recordExpectingError(owner, fundEntry(FUND, "SELL", "2026-04-02", "2000.00", "5"));
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(tooMuch.getBody()).contains("Saldo insuficiente");
    }

    @Test
    @DisplayName("C7: resgatar tudo pelo valor do extrato — sobra de fração de cota (até R$ 0,01) é ajustada ao saldo")
    void redeemAllRounding() {
        recordSuccessfully(owner, fundEntry(FUND, "BUY", "2026-01-05", "1000.00", "3"));      // 333,33333333 cotas
        // 333,33333333 x 3,1 = 1033,333...: o extrato arredonda para 1033,34 -> 333,33548387 cotas (R$ 0,0067 a mais)
        TransactionResponseDTO all = recordSuccessfully(owner, fundEntry(FUND, "SELL", "2026-02-02", "1033.34", "3.1"));
        TransactionResponseDTO saved = transactionsOf(owner).stream().filter(t -> t.id().equals(all.id())).findFirst()
            .orElseThrow();
        assertThat(saved.shares()).isEqualByComparingTo("333.33333333");
        assertThat(jdbc.queryForObject("SELECT is_settled FROM portfolio_assets WHERE id = ?", Boolean.class,
            all.portfolioAssetId())).isTrue();

        // sobra que vale mais que um centavo continua bloqueada
        recordSuccessfully(owner, fundEntry(FUND, "BUY", "2026-03-02", "1000.00", "3"));
        ResponseEntity<String> over = recordExpectingError(owner, fundEntry(FUND, "SELL", "2026-03-03", "1000.10", "3"));
        assertThat(over.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

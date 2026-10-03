package com.primevest.support;

import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementKind;
import com.primevest.domain.integration.source.MovementSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Uma conta de corretora INVENTADA, ja no formato normalizado do conector.
 *
 * <p>Nenhum id, data ou valor vem de conta real (PLANO_CRIPTO_BINANCE.md,
 * 6.13). A conta foi montada para fechar: depositos em reais, compras, uma
 * conversao e uma venda, com o saldo final que a "corretora" mostraria — e e
 * contra ele que o teste de conciliacao confere as regras.</p>
 *
 * <pre>
 * Depositos PIX           1.000,00 + 600,00                    = 1.600,00
 * #1 compra BTC  0,00200000 x 400.000  taxa 0,00000200 BTC     -   800,00
 * #2 compra ETH  0,05000000 x  12.000  taxa 0,60 BRL           -   600,60
 * #3 Convert BRL->BTC       150,00 -> 0,00036123 BTC           -   150,00
 * #4 venda  BTC  0,00050000 x 420.000  taxa 0,21 BRL           +   209,79
 *                                                  saldo BRL  =    259,19
 * saldo BTC = 0,00199800 + 0,00036123 - 0,00050000             = 0,00185923
 * saldo ETH = 0,05000000
 * </pre>
 */
public final class ContaSintetica {

    public static final BigDecimal SALDO_BRL = new BigDecimal("259.19");
    public static final BigDecimal SALDO_BTC = new BigDecimal("0.00185923");
    public static final BigDecimal SALDO_ETH = new BigDecimal("0.05000000");
    public static final BigDecimal DEPOSITADO = new BigDecimal("1600.00");

    private ContaSintetica() {
    }

    /** Os movimentos que fecham a conta acima. */
    public static List<ImportedMovement> movimentos() {
        return List.of(
            deposito("teste:fiat:D1", "1000.00", "2024-01-05T13:00:00Z"),
            deposito("teste:fiat:D2", "600.00", "2024-02-01T13:00:00Z"),
            trade("teste:spot:BTCBRL:1", "BTC", MovementSide.BUY, "0.00200000", "800.00000000",
                "0.00000200", "BTC", "2024-01-05T15:00:00Z"),
            trade("teste:spot:ETHBRL:2", "ETH", MovementSide.BUY, "0.05000000", "600.00000000",
                "0.60", "BRL", "2024-02-01T15:00:00Z"),
            new ImportedMovement("teste:convert:Q3", MovementKind.CONVERT, "BTC", MovementSide.BUY,
                new BigDecimal("0.00036123"), "BRL", new BigDecimal("150.00"), null, null,
                Instant.parse("2024-02-10T12:00:00Z"), "{\"quoteId\":\"Q3\"}"),
            trade("teste:spot:BTCBRL:4", "BTC", MovementSide.SELL, "0.00050000", "210.00000000",
                "0.21", "BRL", "2024-03-01T15:00:00Z"));
    }

    public static ImportedMovement deposito(String id, String valor, String instante) {
        return new ImportedMovement(id, MovementKind.FIAT_DEPOSIT, "BRL", null, null, "BRL",
            new BigDecimal(valor), null, null, Instant.parse(instante), "{}");
    }

    public static ImportedMovement trade(String id, String ativo, MovementSide lado, String qtd, String valor,
                                         String taxa, String moedaTaxa, String instante) {
        return tradeContra(id, ativo, lado, qtd, "BRL", valor, taxa, moedaTaxa, instante);
    }

    public static ImportedMovement tradeContra(String id, String ativo, MovementSide lado, String qtd,
                                               String cotacao, String valor, String taxa, String moedaTaxa,
                                               String instante) {
        return new ImportedMovement(id, MovementKind.TRADE, ativo, lado, new BigDecimal(qtd), cotacao,
            new BigDecimal(valor), taxa == null ? null : new BigDecimal(taxa), moedaTaxa,
            Instant.parse(instante), "{\"id\":\"" + id + "\"}");
    }
}

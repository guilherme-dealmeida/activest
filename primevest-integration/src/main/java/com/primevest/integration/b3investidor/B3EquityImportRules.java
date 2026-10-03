package com.primevest.integration.b3investidor;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.PriceSource;
import com.primevest.domain.integration.pipeline.ImportRules;
import com.primevest.domain.integration.pipeline.ProposedTransaction;
import com.primevest.domain.integration.source.ImportedMovement;
import com.primevest.domain.integration.source.MovementSide;
import com.primevest.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Regras B1–B10 da B3 (docs/planos/PLANO_INTEGRACAO_B3.md, 6.6). A negociacao de renda
 * variavel e a compra e venda de Tesouro Direto (F7) viram transacao; o resto da B3 serve a
 * conferencia, porque o catalogo ja aplica eventos e proventos (principio P3).
 *
 * <p>Pura, como as regras de cripto: o catalogo entra como funcao.</p>
 */
public final class B3EquityImportRules implements ImportRules {

    public static final B3EquityImportRules INSTANCE = new B3EquityImportRules();

    /** Mercados que viram transacao (B6). */
    static final Set<String> SPOT_MARKETS = Set.of("MERCADO A VISTA", "MERCADO FRACIONARIO");

    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private B3EquityImportRules() {
    }

    @Override
    public Result apply(ImportedMovement m, Function<String, Optional<Asset>> catalog) {
        switch (m.kind()) {
            case EQUITY_TRADE -> { /* segue */ }
            case SETTLEMENT -> {
                return Result.no("Liquidação (D+2) de uma negociação: serve de conferência (B7).");
            }
            case STOCK_EVENT -> {
                if (m.market() != null && "ATUALIZACAO".equals(normalize(m.market()))) {
                    return Result.no("Atualização (" + label(m) + "): registro da B3 com a quantidade já em "
                        + "custódia, sem preço; não muda a posição (B8).");
                }
                return Result.no("Evento do papel (" + label(m) + "): o catálogo já aplica na posição (B8).");
            }
            case CASH_EVENT -> {
                return Result.no("Provento (" + label(m) + "): conferido com os proventos da carteira (B8).");
            }
            case RIGHTS -> {
                return Result.no("Direito de subscrição (" + label(m) + "): não vira posição (B9).");
            }
            case CUSTODY_TRANSFER -> {
                return Result.no("Transferência de custódia entre instituições: não muda a posição (B10).");
            }
            case LENDING -> {
                return Result.no("Empréstimo de ativos: não muda a posição (B10).");
            }
            case FIXED_INCOME -> {
                return tesouro(m, catalog);
            }
            case OTHER -> {
                return Result.no("Lançamento da B3 sem regra no PrimeVest (" + label(m) + "): não importado.");
            }
            default -> {
                return Result.no("Movimento " + m.kind() + " não é da B3.");
            }
        }

        // B6 — so mercado a vista e fracionario.
        if (m.market() == null || !SPOT_MARKETS.contains(normalize(m.market()))) {
            return Result.no("Mercado " + (m.market() == null ? "não informado" : m.market())
                + ": o PrimeVest não modela derivativos (B6).");
        }
        if (m.side() == null || m.asset() == null || m.asset().isBlank() || m.tradeDate() == null) {
            return Result.no("Negociação sem ativo, lado ou data.");
        }
        if (!positive(m.grossQuantity()) || !positive(m.quoteAmount())) {
            return Result.no("Negociação sem quantidade ou sem valor.");
        }

        // B3 / B4 — ticker do catalogo, de renda variavel listada; nunca cria ativo.
        String ticker = baseTicker(m.asset());
        Optional<Asset> asset = catalog.apply(ticker);
        if (asset.isEmpty()) {
            return Result.no("Ativo " + ticker + " não está no catálogo do PrimeVest.");
        }
        if (!tracksPosition(asset.get())) {
            return Result.no("O ticker " + ticker + " existe no catálogo, mas não é renda variável negociada na B3.");
        }

        BigDecimal shares = m.grossQuantity();
        BigDecimal price = m.quoteAmount().divide(shares, PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = shares.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal diferenca = total.subtract(m.quoteAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        if (diferenca.abs().compareTo(TOLERANCE) > 0) {
            return Result.no("Diferença de R$ " + diferenca.toPlainString()
                + " entre o calculado e o informado pela B3: acima do arredondamento aceito.");
        }

        return Result.ok(new ProposedTransaction(
            asset.get().getId(),
            ticker,
            m.side() == MovementSide.BUY ? TransactionType.BUY : TransactionType.SELL,
            shares,
            price,
            BigDecimal.ZERO,  // D5: a B3 nao informa corretagem nem emolumentos
            m.tradeDate(),    // B2: data do negocio, sem fuso
            LocalDateTime.ofInstant(m.executedAt(), ZoneOffset.UTC),
            m.externalId(),
            shares,
            m.quoteAmount(),
            diferenca));
    }

    /**
     * F7 — Tesouro Direto pela Movimentacao: a compra vira transacao do titulo do catalogo (o nome da
     * B3 e o ticker do catalogo, ex.: 'Tesouro Selic 2028'), com o preco = valor / quantidade. A venda
     * nao: o PrimeVest a reparte entre os titulos (FIFO) e refaz essas linhas, sem lugar para a origem.
     * Renda fixa privada (CDB...) nao: a Movimentacao nao traz taxa, indice nem o valor de resgate
     * (o vencimento vem com valor zero) — precisa da Posicao de renda fixa.
     */
    private Result tesouro(ImportedMovement m, Function<String, Optional<Asset>> catalog) {
        String titulo = m.asset() == null ? null : m.asset().strip();
        if (titulo == null || !normalize(titulo).startsWith("TESOURO")) {
            return Result.no("Renda fixa (" + label(m) + "): a Movimentação da B3 não traz taxa, índice nem o valor "
                + "de resgate; lance pela tela de transações (F7).");
        }
        String tipo = m.market() == null ? "" : normalize(m.market());
        TransactionType tipoTx = tipo.equals("COMPRA") ? TransactionType.BUY
            : tipo.equals("VENDA") ? TransactionType.SELL : null;
        if (tipoTx == null) {
            return Result.no("Tesouro Direto (" + label(m) + "): só a compra vira transação (F7).");
        }
        if (tipoTx == TransactionType.SELL) {
            // O PrimeVest reparte a venda de Tesouro entre os titulos (FIFO) e refaz essas linhas a cada
            // realocacao: a origem da B3 nao sobreviveria. Fica para lancar pela tela.
            return Result.no("Venda de Tesouro Direto (" + label(m) + "): o PrimeVest reparte a venda entre os "
                + "títulos comprados; lance-a pela tela de transações (F7).");
        }
        if (m.tradeDate() == null || !positive(m.grossQuantity()) || !positive(m.quoteAmount())) {
            return Result.no("Tesouro Direto sem data, quantidade ou valor.");
        }
        Optional<Asset> asset = catalog.apply(titulo);
        if (asset.isEmpty() || asset.get().getAssetType() == null || !asset.get().getAssetType().isTreasury()) {
            return Result.no("Título " + titulo + " não está no catálogo do PrimeVest como Tesouro Direto.");
        }
        BigDecimal shares = m.grossQuantity();
        BigDecimal price = m.quoteAmount().divide(shares, PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = shares.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal diferenca = total.subtract(m.quoteAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        return Result.ok(new ProposedTransaction(asset.get().getId(), asset.get().getTicker(), tipoTx, shares, price,
            BigDecimal.ZERO, m.tradeDate(), LocalDateTime.ofInstant(m.executedAt(), ZoneOffset.UTC), m.externalId(),
            shares, m.quoteAmount(), diferenca));
    }

    /** Renda variavel listada na B3: os tipos cotados pelo fechamento da bolsa (acao, FII, ETF, BDR). */
    @Override
    public boolean tracksPosition(Asset asset) {
        return asset.getAssetType() != null && asset.getAssetType().getPriceSource() == PriceSource.BRAPI;
    }

    /**
     * Saldo vazio = a Posicao nao veio (ex.: nao exportada no simulador), e nao
     * "o investidor nao tem nada": sem comparacao.
     */
    @Override
    public boolean emptyBalanceMeansNoPosition() {
        return false;
    }

    /** B3 — 'PETR4F' (fracionario) e o mesmo papel de 'PETR4'. */
    public static String baseTicker(String ticker) {
        String t = ticker.strip().toUpperCase();
        return t.matches(".*\\dF") ? t.substring(0, t.length() - 1) : t;
    }

    /** Sem acento e em maiusculas, para comparar nomes da B3. */
    static String normalize(String s) {
        return java.text.Normalizer.normalize(s.strip().toUpperCase(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }

    private static String label(ImportedMovement m) {
        return (m.market() != null ? m.market() : m.kind().name()) + (m.asset() != null ? ", " + m.asset() : "");
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}

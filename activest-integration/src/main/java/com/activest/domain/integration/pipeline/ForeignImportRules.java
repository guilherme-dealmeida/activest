package com.activest.domain.integration.pipeline;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetTypeNames;
import com.activest.domain.integration.source.ImportedMovement;
import com.activest.domain.integration.source.MovementKind;
import com.activest.domain.integration.source.MovementSide;
import com.activest.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Regras de importacao de acoes, REITs e ETFs do exterior (docs/planos/PLANO_INTEGRACOES.md, 10.3): a ordem
 * executada vira transacao do ativo do catalogo, na moeda dele (USD), com o cambio da data aplicado pelo
 * TransactionService (PTAX venda, D10). Os tributos do exterior seguem as regras ja cadastradas (moedas).
 *
 * <ul>
 *   <li>O ticker e procurado so em {@code STOCK}, {@code REIT} e {@code ETF_INTERNACIONAL}; nunca cria ativo.</li>
 *   <li>Cotacao aceita: a moeda do ativo, ou a stablecoin do catalogo que a acompanha (USDC/USDT -> USD, D17 de
 *   moedas).</li>
 *   <li>Preco = valor executado / quantidade (8 casas); taxa na moeda do ativo (2 casas), entra no custo (P1).</li>
 * </ul>
 *
 * <p>Pura: o catalogo entra pelo contexto.</p>
 */
public final class ForeignImportRules implements ImportRules {

    public static final ForeignImportRules INSTANCE = new ForeignImportRules();

    /** Prefixo da chave do saldo das acoes da fonte, para nao se confundir com cripto de mesmo ticker (I6). */
    public static final String BALANCE_PREFIX = "equity:";

    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 2;

    private ForeignImportRules() {
    }

    @Override
    public Set<String> assetTypes() {
        return AssetTypeNames.FOREIGN_LISTED;
    }

    @Override
    public boolean tracksPosition(Asset asset) {
        return asset.getAssetType() != null && AssetTypeNames.FOREIGN_LISTED.contains(asset.getAssetType().getName());
    }

    @Override
    public String balanceKey(Asset asset) {
        return BALANCE_PREFIX + asset.getTicker();
    }

    @Override
    public Result apply(ImportedMovement movement, Function<String, Optional<Asset>> catalog) {
        // Sem contexto nao ha como achar a stablecoin da cotacao: so a moeda do proprio ativo e aceita.
        return map(movement, catalog, quote -> Optional.empty());
    }

    @Override
    public Result apply(ImportedMovement movement, ImportContext context) {
        return map(movement, ticker -> context.asset(ticker, assetTypes()),
            quote -> context.asset(quote, Set.of(AssetTypeNames.CRIPTO)));
    }

    static Result map(ImportedMovement m, Function<String, Optional<Asset>> catalog,
                      Function<String, Optional<Asset>> stablecoins) {
        if (m.kind() == MovementKind.TOKENIZED_CONVERT) {
            return Result.no("Conversão de ação em token (bStocks): ainda não é importada. A posição continua como "
                + "estava; se a quantidade na corretora mudar, o aviso de divergência mostra a diferença.");
        }
        if (m.kind() != MovementKind.FOREIGN_TRADE) {
            return Result.no("Movimento " + m.kind() + " não é negociação de ativo do exterior.");
        }
        if (m.side() == null || m.asset() == null || m.asset().isBlank()) {
            return Result.no("Ordem sem ativo ou sem lado (compra/venda).");
        }
        if (!positive(m.grossQuantity()) || !positive(m.quoteAmount())) {
            return Result.no("Ordem sem quantidade executada ou sem valor.");
        }

        String ticker = m.asset().strip().toUpperCase();
        Optional<Asset> found = catalog.apply(ticker);
        if (found.isEmpty()) {
            return Result.no("O ativo " + ticker + " não está no catálogo de ações, REITs e ETFs do exterior do "
                + "Activest.");
        }
        Asset asset = found.get();
        String currency = asset.getCurrencyCode();

        // Cotacao: a moeda do ativo ou uma stablecoin que a acompanha.
        String quote = m.quoteAsset() == null ? "" : m.quoteAsset().strip().toUpperCase();
        if (!quote.equals(currency)) {
            String reference = stablecoins.apply(quote).map(Asset::getReferenceCurrencyCode).orElse(null);
            if (reference == null || !reference.equals(currency)) {
                return Result.no("Ordem paga em " + (quote.isEmpty() ? "moeda não informada" : quote)
                    + ", que não acompanha a moeda do ativo (" + currency + ").");
            }
        }
        String feeAsset = m.feeAsset() == null ? quote : m.feeAsset().strip().toUpperCase();
        if (m.hasFee() && !feeAsset.equals(quote) && !feeAsset.equals(currency)) {
            return Result.no("Taxa cobrada em " + feeAsset + ": só a taxa na moeda da ordem é importada.");
        }

        BigDecimal shares = m.grossQuantity().setScale(PRICE_SCALE, RoundingMode.DOWN);
        BigDecimal price = m.quoteAmount().divide(m.grossQuantity(), PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal fee = (m.hasFee() ? m.feeAmount() : BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = shares.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal difference = total.subtract(m.quoteAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        if (difference.abs().compareTo(CryptoImportRules.TOLERANCE) > 0) {
            return Result.no("Diferença de " + difference.toPlainString() + " " + currency
                + " entre o calculado e o executado pela corretora: acima do arredondamento aceito.");
        }

        return Result.ok(new ProposedTransaction(
            asset.getId(),
            asset.getTicker(),
            m.side() == MovementSide.BUY ? TransactionType.BUY : TransactionType.SELL,
            shares,
            price,
            fee,
            m.executedAt().atZone(CryptoImportRules.BUSINESS_ZONE).toLocalDate(),
            LocalDateTime.ofInstant(m.executedAt(), ZoneOffset.UTC),
            m.externalId(),
            m.grossQuantity(),
            m.quoteAmount(),
            difference,
            null,
            currency));
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}

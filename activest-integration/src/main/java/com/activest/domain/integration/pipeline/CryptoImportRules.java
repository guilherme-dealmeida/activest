package com.activest.domain.integration.pipeline;

import com.activest.domain.asset.Asset;
import com.activest.domain.integration.source.ImportedMovement;
import com.activest.domain.integration.source.MovementKind;
import com.activest.domain.integration.source.MovementSide;
import com.activest.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * As regras R2 a R8 do docs/planos/PLANO_CRIPTO_BINANCE.md (4.4), aplicadas a um movimento.
 * R1 (agrupar os pedacos de uma ordem) e R5 (externalId) sao do conector.
 *
 * <p>Pura: nao consulta banco nem relogio. O catalogo entra como funcao, e o
 * resultado e a transacao proposta ou o motivo de nao importar — os dois
 * legiveis na pre-visualizacao.</p>
 */
public final class CryptoImportRules implements ImportRules {

    /** A instancia que os conectores de cripto devolvem em {@code rules()}. */
    public static final CryptoImportRules INSTANCE = new CryptoImportRules();


    /** Fuso da data de negocio (R3). O instante da fonte vem em UTC. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    /**
     * A unica moeda de cotacao que a primeira versao importa (R6): a moeda base do cadastro
     * (docs/planos/PLANO_CADASTRO_MOEDAS.md, D12). Constante mantida para quem le o codigo da fonte.
     */
    public static final String BRL = com.activest.domain.currency.Currency.BRL;

    /** Diferenca de arredondamento aceita por operacao (4.5). */
    public static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 2;

    private CryptoImportRules() {
    }

    @Override
    public Result apply(ImportedMovement movement, Function<String, Optional<Asset>> catalog) {
        return map(movement, catalog);
    }

    /**
     * Com o contexto: a perna em stablecoin da ordem de acao dos EUA (D6; docs/planos/PLANO_INTEGRACOES.md, 10.3)
     * precisa do cambio da data; o resto segue as regras R2–R8.
     */
    @Override
    public Result apply(ImportedMovement movement, ImportContext context) {
        if (movement.kind() == MovementKind.STABLECOIN_SETTLEMENT) {
            return settlement(movement, context);
        }
        return map(movement, ticker -> context.asset(ticker, assetTypes()));
    }

    /**
     * Compra de ETF paga em USDC = venda de USDC (e a venda do ETF, compra de USDC). A quantidade e o que a corretora
     * debitou ou creditou; o preco em reais e o cambio da moeda que a stablecoin acompanha (USDC -> USD), PTAX venda
     * da data (D10). Sem taxa: ela fica no ativo do exterior (P1).
     */
    static Result settlement(ImportedMovement m, ImportContext context) {
        if (m.side() == null || m.asset() == null || m.asset().isBlank() || !positive(m.grossQuantity())) {
            return Result.no("Liquidação em stablecoin sem moeda, lado ou quantidade.");
        }
        String ticker = m.asset().strip().toUpperCase();
        Optional<Asset> asset = context.asset(ticker, Set.of(com.activest.domain.asset.AssetTypeNames.CRIPTO));
        if (asset.isEmpty()) {
            return Result.no("Ativo " + ticker + " não está no catálogo do Activest.");
        }
        String reference = asset.get().getReferenceCurrencyCode();
        if (reference == null || !reference.equals(m.quoteAsset())) {
            return Result.no(ticker + " não acompanha a moeda " + m.quoteAsset() + " no cadastro.");
        }
        java.time.LocalDate date = m.executedAt().atZone(BUSINESS_ZONE).toLocalDate();
        Optional<BigDecimal> fx = context.fxRate(reference, date);
        if (fx.isEmpty()) {
            return Result.no("Sem câmbio de " + reference + " para " + date + ": a liquidação em " + ticker
                + " entra quando o câmbio do dia for carregado.");
        }
        BigDecimal shares = m.grossQuantity().setScale(PRICE_SCALE, RoundingMode.DOWN);
        BigDecimal price = fx.get().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal valueInBase = shares.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return Result.ok(new ProposedTransaction(
            asset.get().getId(),
            ticker,
            m.side() == MovementSide.BUY ? TransactionType.BUY : TransactionType.SELL,
            shares,
            price,
            BigDecimal.ZERO.setScale(MONEY_SCALE),
            date,
            LocalDateTime.ofInstant(m.executedAt(), ZoneOffset.UTC),
            m.externalId(),
            m.grossQuantity(),
            valueInBase,
            BigDecimal.ZERO.setScale(MONEY_SCALE)));
    }

    @Override
    public Set<String> assetTypes() {
        return Set.of(com.activest.domain.asset.AssetTypeNames.CRIPTO);
    }

    @Override
    public boolean tracksPosition(Asset asset) {
        return asset.getAssetType() != null && asset.getAssetType().isCrypto();
    }

    @Override
    public Set<String> cashBalances() {
        // Moedas do cadastro: saldo em dinheiro, nao posicao (D3; antes a lista fixa BRL/USD/EUR)
        return com.activest.domain.currency.FiatCurrencies.codes();
    }

    public static Result map(ImportedMovement m, Function<String, Optional<Asset>> catalog) {
        switch (m.kind()) {
            case CRYPTO_DEPOSIT, CRYPTO_WITHDRAW -> {
                return Result.no("Transferência entre carteiras: fora da primeira versão (D6).");
            }
            case REWARD -> {
                return Result.no("Rendimento (Earn, staking, airdrop): fora da primeira versão.");
            }
            case FIAT_DEPOSIT -> {
                return Result.no("Depósito em reais não é transação: serve à conferência (4.6).");
            }
            case STABLECOIN_SETTLEMENT -> {
                return Result.no("Liquidação em stablecoin precisa do câmbio da data.");
            }
            case TRADE, CONVERT -> { /* segue */ }
            default -> {
                return Result.no("Movimento " + m.kind() + " não é de corretora de cripto.");
            }
        }

        if (m.side() == null || m.asset() == null || m.asset().isBlank()) {
            return Result.no("Movimento sem ativo ou sem lado (compra/venda).");
        }
        if (!positive(m.grossQuantity()) || !positive(m.quoteAmount())) {
            return Result.no("Movimento sem quantidade ou sem valor.");
        }

        // R6 — so o que ja traz o valor em reais.
        if (!com.activest.domain.currency.FiatCurrencies.base().equals(m.quoteAsset())) {
            return Result.no("Sem valor em reais: operação contra " + m.quoteAsset()
                + ". Fica para quando houver cotação em reais no instante da troca (PENDENCIAS 15).");
        }

        // R7 — o ticker da fonte tem de existir no catalogo, como cripto. Nunca cria ativo.
        String ticker = m.asset().strip().toUpperCase();
        Optional<Asset> asset = catalog.apply(ticker);
        if (asset.isEmpty()) {
            return Result.no("Ativo " + ticker + " não está no catálogo do Activest.");
        }
        if (asset.get().getAssetType() == null || !asset.get().getAssetType().isCrypto()) {
            return Result.no("O ticker " + ticker + " existe no catálogo, mas não é criptomoeda.");
        }

        BigDecimal gross = m.grossQuantity();
        BigDecimal price = m.quoteAmount().divide(gross, PRICE_SCALE, RoundingMode.HALF_UP);
        boolean buy = m.side() == MovementSide.BUY;

        // R2 / R8 — onde a taxa foi cobrada decide a quantidade e a taxa em reais.
        BigDecimal shares = gross;
        BigDecimal fee = BigDecimal.ZERO;
        boolean feeInAsset = false;
        if (m.kind() == MovementKind.TRADE && m.hasFee()) {
            String feeAsset = m.feeAsset() == null ? "" : m.feeAsset().strip().toUpperCase();
            if (feeAsset.equals(ticker)) {
                if (!buy) {
                    return Result.no("Taxa de venda cobrada no próprio ativo: caso ainda não observado na fonte.");
                }
                shares = gross.subtract(m.feeAmount());
                fee = m.feeAmount().multiply(price);
                feeInAsset = true;
            } else if (feeAsset.equals(com.activest.domain.currency.FiatCurrencies.base())) {
                fee = m.feeAmount();
            } else {
                return Result.no("Taxa cobrada em " + (feeAsset.isEmpty() ? "moeda não informada" : feeAsset)
                    + ": precisa do preço em reais no instante da operação (R8, PENDENCIAS 15.3).");
            }
        }
        // R4 — Convert: taxa embutida no preco, nada a separar.
        // numeric(18,8): o que passar de 8 casas e truncado (a fonte nao manda).
        shares = shares.setScale(PRICE_SCALE, RoundingMode.DOWN);
        if (shares.signum() <= 0) {
            return Result.no("A taxa consome toda a quantidade negociada.");
        }
        fee = fee.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // 4.5 — o que o Activest vai registrar x o que a fonte cobrou.
        BigDecimal total = shares.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal recorded = feeInAsset ? total.add(fee) : total;
        BigDecimal difference = recorded.subtract(m.quoteAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        if (difference.abs().compareTo(TOLERANCE) > 0) {
            return Result.no("Diferença de R$ " + difference.toPlainString()
                + " entre o calculado e o cobrado pela fonte: acima do arredondamento aceito (4.5).");
        }

        return Result.ok(new ProposedTransaction(
            asset.get().getId(),
            ticker,
            buy ? TransactionType.BUY : TransactionType.SELL,
            shares,
            price,
            fee,
            m.executedAt().atZone(BUSINESS_ZONE).toLocalDate(),
            LocalDateTime.ofInstant(m.executedAt(), ZoneOffset.UTC),
            m.externalId(),
            gross,
            m.quoteAmount(),
            difference));
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}

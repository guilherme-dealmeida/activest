package com.primevest.support;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.fixedincome.FixedIncomeNote;
import com.primevest.domain.integration.IntegrationConnection;
import com.primevest.domain.integration.IntegrationProvider;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.strategy.ReservationType;
import com.primevest.domain.strategy.Strategy;
import com.primevest.domain.strategy.StrategyReservation;
import com.primevest.domain.asset.AssetType;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import com.primevest.domain.user.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidades de teste, sem Spring: so montagem.
 *
 * <p>Nada de id fixo de catalogo — ativo e tipo vem resolvidos por ticker/nome
 * (ver {@link CatalogIds}), que e a regra do projeto para BIGSERIAL.</p>
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static User user(String email) {
        return User.builder()
            .name("Teste " + email)
            .email(email)
            // bcrypt de "senha-de-teste"; nenhuma senha real entra em teste.
            .passwordHash("$2a$10$7EqJtq98hPqEX7fNZaFWoOa5t1oMuPqjFaZnGnSSVoMWvPb7uZ0/e")
            // Termos aceitos na versao atual: sem isso a app-api responde 403 (TERMS_REQUIRED, D19)
            // a todo teste que nao e sobre os termos.
            .termsVersion(com.primevest.domain.user.LegalTerms.CURRENT_VERSION)
            .termsAcceptedAt(java.time.LocalDateTime.now())
            .build();
    }

    public static User user() {
        return user("teste-" + UUID.randomUUID() + "@primevest.test");
    }

    public static Portfolio portfolio(User user) {
        return Portfolio.builder().user(user).name("Minha Carteira").build();
    }

    public static PortfolioAsset position(Portfolio portfolio, Asset asset) {
        return PortfolioAsset.builder()
            .portfolio(portfolio)
            .asset(asset)
            .appreciation(BigDecimal.ZERO)
            .build();
    }

    public static PortfolioAsset position(Portfolio portfolio, Asset asset, FixedIncomeNote note) {
        PortfolioAsset pa = position(portfolio, asset);
        pa.setNote(note);
        return pa;
    }

    /**
     * Empresa de renda fixa de teste (emissora e distribuidora), achada ou criada pelo nome. A raiz do CNPJ sai do
     * nome ("99" + 6 digitos), fora das faixas reais usadas nos testes.
     */
    public static com.primevest.domain.fixedincome.FixedIncomeIssuer issuer(
            com.primevest.domain.fixedincome.FixedIncomeIssuerRepository repo, String name) {
        if ("Tesouro Nacional".equals(name)) {
            return repo.findFirstByKind(com.primevest.domain.fixedincome.FixedIncomeIssuerKind.TESOURO).orElseThrow();
        }
        String root = "99" + String.format("%06d", Math.floorMod(name.hashCode(), 1_000_000));
        return repo.findByCnpjRoot(root).orElseGet(() -> repo.save(
            com.primevest.domain.fixedincome.FixedIncomeIssuer.builder()
                .kind(com.primevest.domain.fixedincome.FixedIncomeIssuerKind.BANCO)
                .cnpjRoot(root).legalName(name).status("ATIVA")
                .guarantor(com.primevest.domain.fixedincome.Guarantor.FGC).build()));
    }

    /** Titulo com a mesma empresa como emissora e distribuidora. */
    public static FixedIncomeNote note(User user, Asset asset,
                                       com.primevest.domain.fixedincome.FixedIncomeIssuer issuer, LocalDate maturity,
                                       RateType rateType, Indexer indexer, BigDecimal rate) {
        return FixedIncomeNote.builder()
            .user(user)
            .asset(asset)
            .issuer(issuer)
            .distributor(issuer)
            .maturityDate(maturity)
            .rateType(rateType)
            .indexer(indexer)
            .indexerRate(rate)
            .build();
    }

    /** Conexao com a Binance, consentimento aceito agora. */
    public static IntegrationConnection binance(User user, String label) {
        return IntegrationConnection.builder()
            .user(user)
            .provider(IntegrationProvider.BINANCE)
            .label(label)
            .consentAcceptedAt(LocalDateTime.now())
            .build();
    }

    public static Transaction buy(PortfolioAsset pa, LocalDate date, String shares, String price) {
        return transaction(pa, TransactionType.BUY, date, shares, price);
    }

    public static Transaction sell(PortfolioAsset pa, LocalDate date, String shares, String price) {
        return transaction(pa, TransactionType.SELL, date, shares, price);
    }

    /** Lancamento por valor total — Renda Fixa, que nao tem cotas nem preco unitario. */
    public static Transaction byTotal(PortfolioAsset pa, TransactionType type, LocalDate date, String total) {
        return Transaction.builder()
            .portfolioAsset(pa)
            .note(pa.getNote())
            .type(type)
            .totalAmount(new BigDecimal(total))
            .brokerageFee(BigDecimal.ZERO)
            .transactionDate(date)
            .build();
    }

    private static Transaction transaction(PortfolioAsset pa, TransactionType type, LocalDate date,
                                           String shares, String price) {
        BigDecimal qtd = new BigDecimal(shares);
        BigDecimal preco = new BigDecimal(price);
        return Transaction.builder()
            .portfolioAsset(pa)
            .note(pa.getNote())
            .type(type)
            .shares(qtd)
            .pricePerShare(preco)
            .totalAmount(qtd.multiply(preco))
            .brokerageFee(BigDecimal.ZERO)
            .transactionDate(date)
            .build();
    }

    public static Strategy strategy(User user, AssetType type, String targetPercentage) {
        return Strategy.builder()
            .user(user)
            .assetType(type)
            .targetPercentage(new BigDecimal(targetPercentage))
            .build();
    }

    public static StrategyReservation reservationByPercentage(User user, String percentage) {
        return StrategyReservation.builder()
            .user(user)
            .typeReservation(ReservationType.PERCENTAGE)
            .targetPercentage(new BigDecimal(percentage))
            .build();
    }

    public static StrategyReservation reservationByValue(User user, String value) {
        return StrategyReservation.builder()
            .user(user)
            .typeReservation(ReservationType.VALUE)
            .targetValue(new BigDecimal(value))
            .build();
    }
}

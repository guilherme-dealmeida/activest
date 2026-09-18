package com.primevest.domain.portfolio;

import com.primevest.domain.dividend.DividendRepository;
import com.primevest.domain.dividend.PositionDividendTotal;
import com.primevest.domain.portfolio.dto.*;
import com.primevest.domain.portfolio.dto.PortfolioPerformanceDTO.AssetPerformance;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final UserRepository userRepository;
    private final DividendRepository dividendRepository;
    private final PositionCalculator positionCalculator;

    public PortfolioService(PortfolioRepository portfolioRepository, PortfolioAssetRepository portfolioAssetRepository,
                            UserRepository userRepository, DividendRepository dividendRepository,
                            PositionCalculator positionCalculator) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.userRepository = userRepository;
        this.dividendRepository = dividendRepository;
        this.positionCalculator = positionCalculator;
    }

    /**
     * Resumo da carteira. Nenhum total e gravado: tudo sai das posicoes, que
     * por sua vez saem das transacoes.
     *
     * <p>Posicao liquidada fica de fora de todos os totais. Ela nao vale mais
     * nada: o que valia virou dinheiro na venda, e a valorizacao que sobrou
     * gravada e o ganho realizado, nao patrimonio.</p>
     */
    public PortfolioSummaryDTO getPortfolioSummary(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());
        Map<UUID, Position> positions = positionCalculator.positionsOf(portfolio.getId(), assets);
        Map<UUID, BigDecimal> dividends = dividendsByPosition(portfolio.getId());

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal totalDividends = BigDecimal.ZERO;

        for (PortfolioAsset pa : assets) {
            Position position = positions.get(pa.getId());
            // Proventos de posicao liquidada continuam contando: eles foram
            // recebidos, e some-los de repente reescreveria o passado.
            totalDividends = totalDividends.add(dividends.getOrDefault(pa.getId(), BigDecimal.ZERO));
            if (pa.isSettled()) {
                continue;
            }
            totalInvested = totalInvested.add(safe(position.totalInvested()));
            currentValue = currentValue.add(currentValue(pa, position));
        }

        BigDecimal totalReturn = currentValue.add(totalDividends).subtract(totalInvested);
        BigDecimal returnPercentage = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            returnPercentage = totalReturn.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        return new PortfolioSummaryDTO(
            portfolio.getId(), portfolio.getName(),
            totalInvested, currentValue, totalDividends,
            totalReturn, returnPercentage
        );
    }

    /**
     * Gets portfolio assets with calculated values.
     */
    public List<PortfolioAssetDetailDTO> getPortfolioAssets(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());
        Map<UUID, Position> positions = positionCalculator.positionsOf(portfolio.getId(), assets);
        Map<UUID, BigDecimal> dividends = dividendsByPosition(portfolio.getId());
        return assets.stream()
            // So posicoes em aberto: a liquidada acabou, e a sem custo nunca
            // comecou.
            .filter(pa -> !pa.isSettled() && positions.get(pa.getId()).hasInvestment())
            .map(pa -> {
                Position position = positions.get(pa.getId());
                BigDecimal invested = safe(position.totalInvested());
                BigDecimal valorization = appreciation(pa);
                BigDecimal currentValue = currentValue(pa, position);

                BigDecimal currentPrice;
                if (AppreciationUpdater.hasMarketPrice(pa.getAsset())) {
                    currentPrice = pa.getAsset().getCurrentPrice();
                } else {
                    // Sem cotacao (renda fixa e afins): preco implicito pelo valor atual
                    currentPrice = safe(position.totalShares()).compareTo(BigDecimal.ZERO) > 0
                        ? currentValue.divide(position.totalShares(), 4, RoundingMode.HALF_UP)
                        : safe(position.averagePrice());
                }
                BigDecimal returnPct = BigDecimal.ZERO;
                if (invested.compareTo(BigDecimal.ZERO) > 0) {
                    returnPct = valorization.divide(invested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                }

                // A carteira repete ticker desde que a posicao passou a ser por
                // papel: sem emissor e vencimento, duas linhas "CDB" ficam
                // indistinguiveis na tela.
                var note = pa.getNote();

                return new PortfolioAssetDetailDTO(
                    pa.getId(),
                    pa.getAsset().getTicker(),
                    pa.getAsset().getName(),
                    pa.getAsset().getAssetType().getDescription() != null ? pa.getAsset().getAssetType().getDescription() : pa.getAsset().getAssetType().getName(),
                    pa.getAsset().getAssetCategory() != null ? pa.getAsset().getAssetCategory().getDisplayName() : null,
                    note != null ? note.getId() : null,
                    note != null ? note.getIssuer() : null,
                    note != null ? note.getMaturityDate() : null,
                    note != null ? note.getRateType() : null,
                    note != null ? note.getIndexer() : null,
                    note != null ? note.getIndexerRate() : null,
                    safe(position.totalShares()),
                    safe(position.averagePrice()),
                    invested,
                    position.sold(),
                    currentValue,
                    currentPrice,
                    dividends.getOrDefault(pa.getId(), BigDecimal.ZERO),
                    valorization,
                    returnPct,
                    pa.isReservation(),
                    pa.isSettled()
                );
            }).collect(Collectors.toList());
    }

    /**
     * Gets portfolio allocation by asset type.
     */
    public List<PortfolioAllocationDTO> getAllocation(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());
        Map<UUID, Position> positions = positionCalculator.positionsOf(portfolio.getId(), assets);

        // Calculate total current value from assets
        BigDecimal totalVal = BigDecimal.ZERO;
        Map<String, BigDecimal> typeValues = new HashMap<>();
        Map<String, String> typeColors = new HashMap<>();

        for (PortfolioAsset pa : assets) {
            Position position = positions.get(pa.getId());
            if (pa.isSettled() || safe(position.totalInvested()).compareTo(BigDecimal.ZERO) == 0) continue;

            String typeName = pa.getAsset().getAssetType().getDescription() != null
                ? pa.getAsset().getAssetType().getDescription() : pa.getAsset().getAssetType().getName();
            typeColors.put(typeName, pa.getAsset().getAssetType().getColorHex());

            BigDecimal val = currentValue(pa, position);
            totalVal = totalVal.add(val);
            typeValues.merge(typeName, val, BigDecimal::add);
        }

        if (totalVal.compareTo(BigDecimal.ZERO) == 0) return List.of();

        final BigDecimal total = totalVal;
        return typeValues.entrySet().stream().map(e -> {
            BigDecimal pct = e.getValue().divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            return new PortfolioAllocationDTO(e.getKey(), typeColors.get(e.getKey()), e.getValue(), pct);
        }).collect(Collectors.toList());
    }

    /**
     * Gets detailed allocation — grouped by asset type, then by category within each type.
     */
    public List<PortfolioAllocationDetailedDTO> getAllocationDetailed(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());
        Map<UUID, Position> positions = positionCalculator.positionsOf(portfolio.getId(), assets);

        // Calculate total portfolio value
        BigDecimal totalPortfolioValue = BigDecimal.ZERO;

        // Group: typeName -> { colorHex, categoryName -> value }
        Map<String, String> typeColorMap = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> typeCategories = new LinkedHashMap<>();

        for (PortfolioAsset pa : assets) {
            Position position = positions.get(pa.getId());
            if (pa.isSettled() || safe(position.totalInvested()).compareTo(BigDecimal.ZERO) == 0) continue;

            String typeName = pa.getAsset().getAssetType().getDescription() != null
                ? pa.getAsset().getAssetType().getDescription() : pa.getAsset().getAssetType().getName();
            String colorHex = pa.getAsset().getAssetType().getColorHex();
            String categoryName = pa.getAsset().getAssetCategory() != null
                ? pa.getAsset().getAssetCategory().getDisplayName()
                : "Sem Categoria";

            BigDecimal val = currentValue(pa, position);

            totalPortfolioValue = totalPortfolioValue.add(val);
            typeColorMap.put(typeName, colorHex);
            typeCategories.computeIfAbsent(typeName, k -> new LinkedHashMap<>())
                .merge(categoryName, val, BigDecimal::add);
        }

        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) == 0) return List.of();

        final BigDecimal totalVal = totalPortfolioValue;
        List<PortfolioAllocationDetailedDTO> result = new ArrayList<>();

        for (Map.Entry<String, Map<String, BigDecimal>> typeEntry : typeCategories.entrySet()) {
            String typeName = typeEntry.getKey();
            Map<String, BigDecimal> categories = typeEntry.getValue();

            BigDecimal typeTotal = categories.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal typePct = typeTotal.divide(totalVal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

            List<PortfolioAllocationDetailedDTO.CategoryAllocation> catAllocations = categories.entrySet().stream()
                .map(catEntry -> {
                    BigDecimal catPct = typeTotal.compareTo(BigDecimal.ZERO) > 0
                        ? catEntry.getValue().divide(typeTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
                    return new PortfolioAllocationDetailedDTO.CategoryAllocation(
                        catEntry.getKey(), catEntry.getValue(), catPct
                    );
                })
                .collect(Collectors.toList());

            result.add(new PortfolioAllocationDetailedDTO(
                typeName, typeColorMap.get(typeName), typeTotal, typePct, catAllocations
            ));
        }

        return result;
    }

    /**
     * Gets performance.
     */
    public PortfolioPerformanceDTO getPerformance(UUID userId) {
        PortfolioSummaryDTO summary = getPortfolioSummary(userId);
        Portfolio portfolio = getOrCreatePortfolio(userId);

        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());
        Map<UUID, Position> positions = positionCalculator.positionsOf(portfolio.getId(), assets);

        List<AssetPerformance> assetPerfs = assets.stream()
            .filter(pa -> !pa.isSettled() && positions.get(pa.getId()).hasInvestment())
            .map(pa -> {
                Position position = positions.get(pa.getId());
                BigDecimal invested = safe(position.totalInvested());
                BigDecimal currentValue = currentValue(pa, position);
                BigDecimal returnPct = BigDecimal.ZERO;
                if (invested.compareTo(BigDecimal.ZERO) > 0) {
                    returnPct = currentValue.subtract(invested).divide(invested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                }
                return new AssetPerformance(pa.getAsset().getTicker(), invested, currentValue, returnPct);
            }).collect(Collectors.toList());

        return new PortfolioPerformanceDTO(
            summary.totalInvested(), summary.currentValue(), summary.totalDividends(),
            summary.totalReturn(), summary.returnPercentage(), assetPerfs
        );
    }

    @Transactional
    public Portfolio getOrCreatePortfolio(UUID userId) {
        return portfolioRepository.findFirstByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId).orElseThrow();
            Portfolio p = new Portfolio();
            p.setUser(user);
            p.setName("Main Portfolio");
            return portfolioRepository.save(p);
        });
    }

    /**
     * Valorizacao da posicao: sempre a gravada em portfolio_assets.appreciation.
     *
     * <p>A leitura nao calcula. Com cotacao, quem grava e o
     * {@link AppreciationUpdater} (cotas x cotacao - custo), a cada atualizacao
     * de cotacao ou de transacao. Sem cotacao (renda fixa, Tesouro, tipos
     * MANUAL), o valor e apurado por fora — na Renda Fixa, pela propria venda
     * (ver FixedIncomeSettlement).</p>
     */
    private BigDecimal appreciation(PortfolioAsset pa) {
        return safe(pa.getAppreciation());
    }

    /**
     * Valor atual = valor aplicado + valorizacao - resgatado. Nunca e gravado.
     *
     * <p>O resgate so aparece nos tipos lancados por valor (Renda Fixa), onde
     * a venda tira dinheiro da posicao sem tirar cotas: la o aplicado e a soma
     * das compras e o resgatado fica a parte, porque as regras E e F comparam
     * um com o outro. Nos tipos por cota a venda ja reduziu o custo pelo preco
     * medio, e o resgatado e zero.</p>
     */
    private BigDecimal currentValue(PortfolioAsset pa, Position position) {
        return safe(position.totalInvested()).add(appreciation(pa)).subtract(position.sold());
    }

    /**
     * Proventos por posicao: a soma de dividends.total_amount de cada uma. Uma
     * consulta para a carteira inteira, em vez de uma por posicao.
     */
    private Map<UUID, BigDecimal> dividendsByPosition(UUID portfolioId) {
        return dividendRepository.sumTotalAmountByPosition(portfolioId).stream()
            .collect(Collectors.toMap(PositionDividendTotal::portfolioAssetId, t -> safe(t.total())));
    }

    private BigDecimal safe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}

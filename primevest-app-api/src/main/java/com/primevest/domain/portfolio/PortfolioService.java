package com.primevest.domain.portfolio;

import com.primevest.domain.asset.AssetLogos;

import com.primevest.domain.dividend.DividendCalendar;
import com.primevest.domain.dividend.DividendRepository;
import com.primevest.domain.dividend.PositionDividendTotal;
import com.primevest.domain.portfolio.dto.*;
import com.primevest.domain.portfolio.dto.PortfolioPerformanceDTO.AssetPerformance;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.exception.BusinessException;
import com.primevest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PortfolioService {

    /** Bonificacoes sem custo (icone na Carteira). Opcional nos testes que montam o servico a mao. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    BonusCostService bonusCosts;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final UserRepository userRepository;
    private final DividendRepository dividendRepository;
    private final PositionCalculator positionCalculator;
    private final DividendCalendar dividendCalendar;
    private final AssetLogos assetLogos;
    private final PositionValuationInfo valuationInfo;

    public PortfolioService(PortfolioRepository portfolioRepository, PortfolioAssetRepository portfolioAssetRepository,
                            UserRepository userRepository, DividendRepository dividendRepository,
                            PositionCalculator positionCalculator, DividendCalendar dividendCalendar,
                            AssetLogos assetLogos, PositionValuationInfo valuationInfo) {
        this.assetLogos = assetLogos;
        this.valuationInfo = valuationInfo;
        this.portfolioRepository = portfolioRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.userRepository = userRepository;
        this.dividendRepository = dividendRepository;
        this.positionCalculator = positionCalculator;
        this.dividendCalendar = dividendCalendar;
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
            // Sem custo a posicao nunca comecou (ou teve todas as transacoes
            // excluidas): fica fora, como na listagem e na alocacao. Sem isto
            // uma valorizacao que sobrou gravada virava "valor atual" sozinha.
            if (pa.isSettled() || !position.hasInvestment()) {
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
        List<PortfolioAsset> open = assets.stream()
            // So posicoes em aberto: a liquidada acabou, e a sem custo nunca
            // comecou.
            .filter(pa -> !pa.isSettled() && positions.get(pa.getId()).hasInvestment())
            .toList();
        Map<UUID, PositionValuationInfo.Info> infos = valuationInfo != null ? valuationInfo.of(open) : null;
        Map<UUID, List<BonusCostService.Pending>> bonus = bonusCosts != null ? bonusCosts.pendingFor(open) : Map.of();
        return open.stream()
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
                        ? currentValue.divide(position.totalShares(), 8, RoundingMode.HALF_UP)
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
                    note != null ? note.getPurchaseDate() : null,
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
                    pa.isSettled(),
                    pa.getAsset().getAssetType().isCrypto(),
                    assetLogos.logoFlag(pa.getAsset()),
                    pa.getAsset().getIssuer() != null ? pa.getAsset().getIssuer().getId() : null,
                    info(infos, pa) != null ? info(infos, pa).basis() : null,
                    info(infos, pa) != null ? info(infos, pa).date() : null,
                    info(infos, pa) != null ? info(infos, pa).note() : null,
                    info(infos, pa) != null && info(infos, pa).matured(),
                    bonus.getOrDefault(pa.getId(), List.of()).stream()
                        .map(b -> new PortfolioAssetDetailDTO.PendingBonusCost(b.stockEventId(), b.dataCom(),
                            b.factor(), b.newShares(), b.label()))
                        .toList(),
                    info(infos, pa) != null ? info(infos, pa).estimatedNet() : null,
                    info(infos, pa) == null ? List.of() : info(infos, pa).lots().stream()
                        .map(l -> new PortfolioAssetDetailDTO.FixedIncomeLot(l.date(), l.days(), l.applied(),
                            l.balance(), l.income(), l.iofRate(), l.irRate(), l.iof(), l.ir(), l.net()))
                        .toList()
                );
            }).collect(Collectors.toList());
    }

    /**
     * Marca ou desmarca a posicao como reserva de emergencia.
     *
     * <p>Recebe o ESTADO desejado, nao um "inverta": o switch da carteira pode
     * ser clicado duas vezes antes da primeira resposta chegar, e um toggle
     * cego deixaria o resultado na mao da ordem de chegada. Reenviar a mesma
     * requisicao nao muda nada.</p>
     *
     * <p>So renda fixa compoe reserva. A regra olha a coluna fixed_income do
     * tipo, e nao uma lista de nomes: tipo novo de renda fixa ja nasce
     * elegivel, e nenhum lugar precisa ser lembrado de incluir o nome dele.</p>
     *
     * <p>Posicao de outro dono responde "nao encontrada", nunca "sem
     * permissao" — id que existe e exatamente o que confirmaria o alvo.</p>
     */
    @Transactional
    public PortfolioAssetReservationDTO setReservation(UUID userId, UUID positionId, boolean isReservation) {
        PortfolioAsset position = portfolioAssetRepository.findOwnedById(userId, positionId)
            .orElseThrow(() -> new ResourceNotFoundException("Posição não encontrada"));

        if (!position.getAsset().getAssetType().isFixedIncome()) {
            throw new BusinessException(
                "Somente ativos de renda fixa podem compor a reserva de emergência");
        }

        position.setReservation(isReservation);
        portfolioAssetRepository.save(position);

        return new PortfolioAssetReservationDTO(
            position.getId(),
            position.getAsset().getTicker(),
            position.isReservation()
        );
    }

    /**
     * Alocacao atual entre reserva de emergencia e geracao de renda.
     *
     * <p>Mesmo filtro e mesmo valor de {@link #getAllocation}: posicao em
     * aberto, pelo valor atual. E o grafico ao lado do "por tipo" na aba
     * Carteira de Investimentos; como aquele exclui a reserva, a geracao de
     * renda daqui e exatamente o total de la.</p>
     */
    public ReservationAllocationDTO getReservationAllocation(UUID userId) {
        Portfolio portfolio = getOrCreatePortfolio(userId);
        List<PortfolioAsset> assets = portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());
        Map<UUID, Position> positions = positionCalculator.positionsOf(portfolio.getId(), assets);

        BigDecimal reservation = BigDecimal.ZERO;
        BigDecimal income = BigDecimal.ZERO;
        for (PortfolioAsset pa : assets) {
            Position position = positions.get(pa.getId());
            if (pa.isSettled() || safe(position.totalInvested()).compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal val = currentValue(pa, position);
            if (pa.isReservation()) {
                reservation = reservation.add(val);
            } else {
                income = income.add(val);
            }
        }

        BigDecimal total = reservation.add(income);
        return new ReservationAllocationDTO(
            total,
            reservation,
            share(reservation, total),
            income,
            share(income, total)
        );
    }

    private BigDecimal share(BigDecimal value, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    /**
     * Alocacao da carteira por tipo de ativo.
     *
     * <p>{@code excludeReservation} tira as posicoes de reserva de emergencia
     * da conta, com o mesmo criterio do rebalanceamento: a reserva e um bloco
     * proprio, e o que sobra e a geracao de renda. A tela Alocacao pede assim;
     * o Dashboard nao, porque ali a pergunta e a carteira inteira.</p>
     */
    public List<PortfolioAllocationDTO> getAllocation(UUID userId, boolean excludeReservation) {
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
            if (excludeReservation && pa.isReservation()) continue;

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
     * Alocacao por tipo e, dentro de cada tipo, por categoria.
     *
     * <p>E a geracao de renda: posicao de reserva de emergencia nunca entra,
     * com o mesmo criterio do rebalanceamento. So a tela Alocacao consome este
     * endpoint, e ali a reserva tem o seu grafico proprio.</p>
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
        // Ordem de cadastro do tipo de ativo: e ela que a tela segue, e nao a
        // ordem em que as posicoes aparecem na carteira.
        Map<String, Long> typeOrder = new HashMap<>();

        for (PortfolioAsset pa : assets) {
            Position position = positions.get(pa.getId());
            if (pa.isSettled() || safe(position.totalInvested()).compareTo(BigDecimal.ZERO) == 0) continue;
            if (pa.isReservation()) continue;

            String typeName = pa.getAsset().getAssetType().getDescription() != null
                ? pa.getAsset().getAssetType().getDescription() : pa.getAsset().getAssetType().getName();
            String colorHex = pa.getAsset().getAssetType().getColorHex();
            String categoryName = pa.getAsset().getAssetCategory() != null
                ? pa.getAsset().getAssetCategory().getDisplayName()
                : "Sem Categoria";

            BigDecimal val = currentValue(pa, position);

            totalPortfolioValue = totalPortfolioValue.add(val);
            typeColorMap.put(typeName, colorHex);
            typeOrder.putIfAbsent(typeName, pa.getAsset().getAssetType().getId());
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

        result.sort(Comparator.comparing(item -> typeOrder.getOrDefault(item.typeName(), Long.MAX_VALUE)));

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
     * Proventos por posicao: a soma LIQUIDA de dividends (net_amount; total_amount
     * onde o tipo nao tem regra de IR — docs/planos/PLANO_IR_PROVENTOS.md, D1), so do que ja
     * foi RECEBIDO: provento com pagamento depois de hoje (fuso da B3) fica de
     * fora. Uma consulta para a carteira inteira, em vez de uma por posicao.
     */
    private Map<UUID, BigDecimal> dividendsByPosition(UUID portfolioId) {
        return dividendRepository.sumTotalAmountByPosition(portfolioId, dividendCalendar.today()).stream()
            .collect(Collectors.toMap(PositionDividendTotal::portfolioAssetId, t -> safe(t.total())));
    }

    private BigDecimal safe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private static PositionValuationInfo.Info info(Map<UUID, PositionValuationInfo.Info> infos, PortfolioAsset pa) {
        return infos == null ? null : infos.get(pa.getId());
    }
}

package com.primevest.domain.strategy;

import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.portfolio.Position;
import com.primevest.domain.portfolio.PositionCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O que cada posicao da carteira ainda vale para a estrategia.
 *
 * <p>Carrega posicoes e transacoes uma vez e responde as duas perguntas das
 * telas de estrategia: quanto vale cada posicao, e como a carteira se divide
 * entre reserva de emergencia e geracao de renda. O rebalanceamento por tipo
 * e a estrategia de reserva leem daqui para que as duas contem o mesmo
 * dinheiro do mesmo jeito.</p>
 */
@Component
public class StrategyHoldings {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PositionCalculator positionCalculator;

    public StrategyHoldings(PortfolioRepository portfolioRepository,
                            PortfolioAssetRepository portfolioAssetRepository,
                            PositionCalculator positionCalculator) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioAssetRepository = portfolioAssetRepository;
        this.positionCalculator = positionCalculator;
    }

    public Holdings of(UUID userId) {
        var portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        if (portfolio == null) {
            return new Holdings(List.of(), Map.of());
        }
        List<PortfolioAsset> assets =
            portfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly(portfolio.getId());
        return new Holdings(assets, positionCalculator.positionsOf(portfolio.getId(), assets));
    }

    public record Holdings(List<PortfolioAsset> assets, Map<UUID, Position> positions) {

        /**
         * O que a posicao ainda tem na carteira.
         *
         * <p>Posicao liquidada nao entra: ela virou dinheiro e nao ocupa mais
         * espaco na alocacao. Na Renda Fixa o valor aplicado nao desconta os
         * resgates — as regras E e F precisam das duas pontas separadas —,
         * entao o desconto e feito aqui.</p>
         */
        public BigDecimal held(PortfolioAsset pa) {
            Position position = positions.get(pa.getId());
            if (position == null || pa.isSettled()) {
                return BigDecimal.ZERO;
            }
            return position.invested().subtract(position.sold()).max(BigDecimal.ZERO);
        }

        /** A carteira inteira: reserva + geracao de renda. */
        public BigDecimal total() {
            return assets.stream().map(this::held).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** So as posicoes marcadas como reserva de emergencia. */
        public BigDecimal reservation() {
            return assets.stream()
                .filter(PortfolioAsset::isReservation)
                .map(this::held)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}

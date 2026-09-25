package com.primevest.domain.dividend;

import com.primevest.domain.dividend.dto.DividendResponseDTO;
import com.primevest.domain.dividend.dto.DividendSummaryDTO;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetLogoService;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.asset.AssetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DividendService {

    private final DividendRepository dividendRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final DividendCalendar dividendCalendar;

    /**
     * Gets dividend summary for the user.
     */
    public DividendSummaryDTO getDividendSummary(UUID userId) {
        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        if (portfolio == null) {
            return new DividendSummaryDTO(BigDecimal.ZERO, Map.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // Proventos da carteira com posicao, ativo e tipo numa consulta so
        List<Dividend> allDividends = dividendRepository.findByPortfolioIdWithAsset(portfolio.getId());

        // Recebido x a receber: pagamento ate hoje, no fuso da B3 (Dividend.isReceived)
        LocalDate today = dividendCalendar.today();
        List<Dividend> received = allDividends.stream().filter(d -> d.isReceived(today)).toList();
        List<Dividend> toReceive = allDividends.stream().filter(d -> !d.isReceived(today)).toList();

        // Totais: liquido (o que entrou), bruto e IR retido (PLANO_IR_PROVENTOS.md, D3)
        BigDecimal total = sum(received, Dividend::netOrGross);
        BigDecimal totalGross = sum(received, Dividend::getTotalAmount);
        BigDecimal totalTax = sum(received, Dividend::getWithholdingTax);

        // Group by type
        Map<String, BigDecimal> byType = new HashMap<>();
        byType.put("DIVIDENDO", BigDecimal.ZERO);
        byType.put("JCP", BigDecimal.ZERO);
        byType.put("RENDIMENTO", BigDecimal.ZERO);
        byType.put("AMORTIZACAO", BigDecimal.ZERO);
        byType.put("RESTITUICAO_CAPITAL", BigDecimal.ZERO);
        for (Dividend d : received) {
            String typeName = d.getType().name();
            BigDecimal amount = d.netOrGross() != null ? d.netOrGross() : BigDecimal.ZERO;
            byType.merge(typeName, amount, BigDecimal::add);
        }

        // Recent dividends (a consulta ja vem por exDate desc)
        List<DividendResponseDTO> recent = allDividends.stream()
            .map(d -> toResponseDTO(d, today))
            .collect(Collectors.toList());

        return new DividendSummaryDTO(total, byType, recent, totalGross, totalTax,
            sum(toReceive, Dividend::netOrGross), sum(toReceive, Dividend::getTotalAmount),
            sum(toReceive, Dividend::getWithholdingTax));
    }

    private static BigDecimal sum(List<Dividend> dividends, java.util.function.Function<Dividend, BigDecimal> field) {
        return dividends.stream()
            .map(field)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Gets dividends by asset ticker.
     */
    public List<DividendResponseDTO> getDividendsByAsset(UUID userId, String ticker) {
        Asset asset = assetRepository.findByTicker(ticker).orElse(null);
        if (asset == null) return List.of();

        LocalDate today = dividendCalendar.today();
        return dividendRepository.findByAssetIdAndOwner(userId, asset.getId())
            .stream()
            .map(d -> toResponseDTO(d, today))
            .collect(Collectors.toList());
    }

    private DividendResponseDTO toResponseDTO(Dividend d, LocalDate today) {
        PortfolioAsset pa = d.getPortfolioAsset();
        Asset asset = pa != null ? pa.getAsset() : null;
        AssetType assetType = asset != null ? asset.getAssetType() : null;
        return new DividendResponseDTO(
            d.getId(),
            asset != null ? asset.getTicker() : "",
            asset != null ? asset.getName() : "",
            d.getAmountPerShare(),
            d.getTotalAmount(),
            d.getExDate(),
            d.getPaymentDate(),
            d.getType(),
            d.getSharesOnDate(),
            d.getWithholdingRate(),
            d.getWithholdingTax(),
            d.getNetAmountPerShare(),
            d.getNetAmount(),
            d.getWithholdingRule() != null ? d.getWithholdingRule().getLegalBasis() : null,
            assetType != null ? assetType.getId() : null,
            assetType != null ? assetType.getDescription() : null,
            // Mesma regra de PortfolioService: null = tipo sem logo.
            AssetLogoService.typeHasLogo(assetType) ? asset.getLogoPath() != null : null,
            d.isReceived(today),
            d.getCashEvent() != null ? d.getCashEvent().getSource().name() : null,
            d.getCashEvent() != null && d.getCashEvent().getPaymentDateSource() != null
                ? d.getCashEvent().getPaymentDateSource().name() : null
        );
    }
}

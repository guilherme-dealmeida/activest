package com.activest.domain.dividend;

import com.activest.domain.dividend.dto.DividendResponseDTO;
import com.activest.domain.dividend.dto.DividendSummaryDTO;
import com.activest.domain.portfolio.Portfolio;
import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.PortfolioRepository;
import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetLogos;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.AssetType;
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
    private final AssetLogos assetLogos;

    /**
     * Gets dividend summary for the user.
     */
    public DividendSummaryDTO getDividendSummary(UUID userId) {
        Portfolio portfolio = portfolioRepository.findFirstByUserId(userId).orElse(null);
        if (portfolio == null) {
            return new DividendSummaryDTO(BigDecimal.ZERO, Map.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // Proventos da carteira com posicao, ativo e tipo numa consulta so
        List<Dividend> allDividends = dividendRepository.findByPortfolioIdWithAsset(portfolio.getId());

        // Recebido x a receber: pagamento ate hoje, no fuso da B3 (Dividend.isReceived)
        LocalDate today = dividendCalendar.today();
        List<Dividend> received = allDividends.stream().filter(d -> d.isReceived(today)).toList();
        List<Dividend> toReceive = allDividends.stream().filter(d -> !d.isReceived(today)).toList();

        // Totais: liquido (o que entrou), bruto e IR retido (docs/planos/PLANO_IR_PROVENTOS.md, D3)
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

        // Ultimos 12 meses: recebidos com pagamento (ou, sem ele, data ex) depois de hoje - 12 meses
        LocalDate from12m = today.minusMonths(12);
        List<Dividend> last12 = received.stream()
            .filter(d -> {
                LocalDate ref = d.getPaymentDate() != null ? d.getPaymentDate() : d.getExDate();
                return ref != null && ref.isAfter(from12m);
            })
            .toList();

        return new DividendSummaryDTO(total, byType, recent, totalGross, totalTax,
            sum(toReceive, Dividend::netOrGross), sum(toReceive, Dividend::getTotalAmount),
            sum(toReceive, Dividend::getWithholdingTax), sum(last12, Dividend::netOrGross));
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    com.activest.domain.currency.CurrencyRepository currencies;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    com.activest.domain.currency.ExchangeRateService exchangeRates;

    /** Moeda e tributos do exterior do provento; nulo em reais (PLANO_CADASTRO_MOEDAS, C6). */
    private DividendResponseDTO.Foreign foreign(Dividend d) {
        if (d.getCurrencyCode() == null || (exchangeRates != null ? exchangeRates.isBase(d.getCurrencyCode())
                : com.activest.domain.currency.Currency.BRL.equals(d.getCurrencyCode()))) {
            return null;
        }
        String symbol = currencies == null ? d.getCurrencyCode() : currencies.findById(d.getCurrencyCode())
            .map(com.activest.domain.currency.Currency::getSymbol).orElse(d.getCurrencyCode());
        var rule = d.getForeignTaxRule();
        return new DividendResponseDTO.Foreign(d.getCurrencyCode(), symbol, d.getFxRate(), d.getOriginalAmountPerShare(),
            d.getOriginalTotalAmount(), d.getForeignWithholdingRate(), d.getForeignWithholdingTax(),
            rule != null ? rule.getLegalBasis() : null, d.getForeignIncomeTax());
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
            assetLogos.logoFlag(asset),
            d.isReceived(today),
            d.getCashEvent() != null ? d.getCashEvent().getSource().name() : null,
            d.getCashEvent() != null && d.getCashEvent().getPaymentDateSource() != null
                ? d.getCashEvent().getPaymentDateSource().name() : null,
            asset != null && asset.getIssuer() != null ? asset.getIssuer().getId() : null,
            foreign(d)
        );
    }
}

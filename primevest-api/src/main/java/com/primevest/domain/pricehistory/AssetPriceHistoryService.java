package com.primevest.domain.pricehistory;

import com.primevest.domain.pricehistory.dto.PriceHistoryDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssetPriceHistoryService {

    /**
     * Gets price history.
     */
    public List<PriceHistoryDTO> getHistory(String ticker, LocalDate start, LocalDate end) {
        return List.of();
    }

    /**
     * Saves price history.
     */
    @Transactional
    public void saveHistory(Long assetId, PriceHistoryDTO dto) {
    }
}

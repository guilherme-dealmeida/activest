package com.activest.adm.domain.asset;

import com.activest.adm.domain.notice.AdminNotices;
import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetRepository;
import com.activest.domain.asset.DelistingSource;
import com.activest.domain.notification.NotificationKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Curadoria da inativação (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C7 e 11.5): o administrador inativa ou desfaz a
 * inativação de um papel. Grava a origem {@code MANUAL}, que a conferência do catálogo nunca refaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetActivationService {

    private final AssetRepository assets;
    private final AdminNotices notices;

    /**
     * {@code active = true}: volta a ser listado (sem data de saída). {@code active = false}: inativo, com a data de
     * saída informada (nula = sem data — sem limite para a data da transação).
     */
    public record Activation(Boolean active, LocalDate delistedOn) {
    }

    /**
     * @throws IllegalArgumentException ativo inexistente
     * @throws IllegalStateException    pedido incompleto ({@code active} ausente, ou data de saída num papel ativo)
     */
    @Transactional
    public Asset apply(Long assetId, Activation body) {
        Asset a = assets.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Ativo nao encontrado: " + assetId));
        if (body == null || body.active() == null) {
            throw new IllegalStateException("Informe active (true para listado, false para inativo).");
        }
        if (body.active() && body.delistedOn() != null) {
            throw new IllegalStateException("Papel ativo não tem data de saída.");
        }
        a.setActive(body.active());
        a.setDelistedOn(body.active() ? null : body.delistedOn());
        a.setDelistingSource(DelistingSource.MANUAL);
        Asset saved = assets.saveAndFlush(a);
        if (body.active()) {
            // Desfeita a inativacao, o aviso (e o que ele levou a quem tem posicao) se resolve
            notices.resolve(NotificationKind.CATALOGO_TICKER_INATIVADO, CatalogDelisting.key(a.getId()));
        }
        log.warn("Curadoria: {} {} (MANUAL){}", a.getTicker(), body.active() ? "reativado" : "inativado",
                body.delistedOn() != null ? ", saida em " + body.delistedOn() : "");
        return saved;
    }
}

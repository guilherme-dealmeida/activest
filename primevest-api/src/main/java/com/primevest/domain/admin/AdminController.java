package com.primevest.domain.admin;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.asset.AssetLogoService;
import com.primevest.domain.asset.CatalogAuditService;
import com.primevest.domain.dividend.ProventosSyncService;
import com.primevest.domain.pricehistory.ClosingPriceService;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Disparo manual das rotinas externas (PLANO_BRAPI.md: "semanal e sob
 * demanda"). So para usuario com {@code is_admin}.
 *
 * <p>As rotinas rodam em segundo plano e respondem 202: o resultado sai no log
 * da API (no Docker, {@code .\\.claude\\build.ps1 -Docker} grava em
 * .claude/logs/docker-api.log).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final CatalogAuditService catalogAuditService;
    private final AssetLogoService assetLogoService;
    private final ClosingPriceService closingPriceService;
    private final ProventosSyncService proventosSyncService;

    @PostMapping("/catalog-audit")
    public ResponseEntity<Map<String, String>> catalogAudit() {
        return start("conferencia do catalogo", () -> catalogAuditService.run("sob demanda"));
    }

    @PostMapping("/logos")
    public ResponseEntity<Map<String, String>> logos() {
        return start("logos", () -> assetLogoService.sync(false, "sob demanda"));
    }

    @PostMapping("/closing")
    public ResponseEntity<Map<String, String>> closing() {
        return start("fechamento", () -> {
            closingPriceService.runB3Closing("sob demanda");
            closingPriceService.runCryptoClosing("sob demanda");
        });
    }

    @PostMapping("/proventos")
    public ResponseEntity<Map<String, String>> proventos() {
        return start("proventos", () -> proventosSyncService.syncAll("sob demanda"));
    }

    private ResponseEntity<Map<String, String>> start(String name, Runnable routine) {
        User user = userRepository.findById(SecurityUtils.getCurrentUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente administrador.");
        }
        Thread.ofVirtual().name("admin-" + name.replace(' ', '-')).start(() -> {
            try {
                routine.run();
            } catch (RuntimeException e) {
                log.error("Rotina '{}' sob demanda falhou: {}", name, e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body(Map.of("status", "iniciada", "rotina", name,
            "resultado", "no log da API"));
    }
}

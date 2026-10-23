package com.activest.domain.notification;

import com.activest.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** A caixa de notificações (docs/planos/PLANO_NOTIFICACOES.md, 5.2). */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationBoxService service;

    @GetMapping
    public NotificationDTOs.Page list(@RequestParam(defaultValue = "INBOX") String folder,
                                      @RequestParam(required = false) String audience,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "false") boolean unread,
                                      @RequestParam(defaultValue = "0") int page) {
        return service.list(SecurityUtils.getCurrentUserId(), folder, audience, category, status, unread, page);
    }

    @GetMapping("/summary")
    public NotificationDTOs.Summary summary() {
        return service.summary(SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/{id}")
    public NotificationDTOs.Item get(@PathVariable UUID id) {
        return service.get(SecurityUtils.getCurrentUserId(), id);
    }

    @PostMapping("/{id}/open")
    public NotificationDTOs.Item open(@PathVariable UUID id) {
        return service.open(SecurityUtils.getCurrentUserId(), id);
    }

    @PatchMapping("/state")
    public NotificationDTOs.StateResult state(@RequestBody NotificationDTOs.StateRequest req) {
        return service.applyState(SecurityUtils.getCurrentUserId(), req);
    }

    @PatchMapping("/{id}/status")
    public NotificationDTOs.Item status(@PathVariable UUID id, @RequestBody NotificationDTOs.StatusRequest req) {
        return service.changeStatus(SecurityUtils.getCurrentUserId(), id, req);
    }
}

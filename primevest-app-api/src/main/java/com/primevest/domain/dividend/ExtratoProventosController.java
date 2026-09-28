package com.primevest.domain.dividend;

import com.primevest.config.SecurityUtils;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Extrato de proventos da Area do Investidor da B3 para a carteira de quem chama (V24).
 *
 * <p>Continua na app-api quando o resto do {@code /api/v1/admin} foi para a adm-api
 * (PLANO_ADM_API.md, 10.2 e decisao A3): casa o extrato com os proventos do usuario e
 * recalcula a carteira dele na mesma transacao — e importacao da carteira, nao rotina de
 * catalogo. Rota e exigencia de admin mantidas ate virar tela (escopo de importacoes).</p>
 *
 * <p>Sincrono: a resposta traz a situacao de cada linha. {@code dryRun: true} confere tudo e
 * desfaz — o script de carga roda assim primeiro (E:\\carga\\scripts\\carregar_proventos.mjs).</p>
 */
@RestController
@RequestMapping("/api/v1/admin/proventos")
@RequiredArgsConstructor
public class ExtratoProventosController {

    private final UserRepository userRepository;
    private final ExtratoProventosImportService extratoProventosImportService;

    @PostMapping("/extrato")
    public ResponseEntity<ExtratoProventosImportService.Result> proventosExtrato(
            @RequestBody ExtratoProventosImportService.Request request) {
        User user = userRepository.findById(SecurityUtils.getCurrentUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente administrador.");
        }
        return ResponseEntity.ok(extratoProventosImportService.importFor(user.getId(), request));
    }
}

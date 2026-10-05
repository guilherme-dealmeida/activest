package com.primevest.domain.auth.verification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.domain.auth.AuthSettings;
import com.primevest.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Códigos de verificação por e-mail (docs/planos/PLANO_CADASTRO_USUARIO.md, 3.2).
 *
 * <ul>
 *   <li>6 dígitos do {@link SecureRandom}; guardado só o HMAC (com o id do pedido junto);</li>
 *   <li>vale {@code code-ttl-minutes} (10); {@code max-attempts} (5) erros invalidam o pedido;</li>
 *   <li>reenvio depois de {@code resend-seconds} (60), no máximo {@code max-sends-per-hour} (5) envios
 *       por e-mail e propósito.</li>
 * </ul>
 *
 * <p>Aqui só se emite e confere: quem chama decide o que mandar por e-mail (o código, ou o aviso de
 * "você já tem conta" num pedido de fachada).</p>
 */
@Service
public class VerificationCodeService {

    /** O código em claro só existe no retorno de {@link #issue}/{@link #resend}, para ir ao e-mail. */
    public record Issued(PendingCode pending, String code) {}

    private static final String KEY_CODE = "code:";
    private static final String KEY_SENDS = "sends:";
    private static final Duration UMA_HORA = Duration.ofHours(1);

    private final VerificationStore store;
    private final SecretHasher hasher;
    private final AuthSettings settings;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public VerificationCodeService(VerificationStore store, SecretHasher hasher, AuthSettings settings,
                                   ObjectMapper json) {
        this(store, hasher, settings, json, Clock.systemUTC());
    }

    VerificationCodeService(VerificationStore store, SecretHasher hasher, AuthSettings settings,
                            ObjectMapper json, Clock clock) {
        this.store = store;
        this.hasher = hasher;
        this.settings = settings;
        this.json = json;
        this.clock = clock;
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public Issued issue(String requestId, VerificationPurpose purpose, String email, Map<String, String> payload,
                        boolean decoy) {
        contarEnvio(purpose, email);
        String code = gerarCodigo();
        PendingCode pending = new PendingCode(requestId, purpose, email, hash(requestId, code), 0,
            agora(), decoy, payload);
        salvar(pending);
        return new Issued(pending, code);
    }

    /** Código novo para o mesmo pedido (o anterior deixa de valer), respeitando a espera e o limite. */
    public Issued resend(String requestId, VerificationPurpose purpose) {
        PendingCode atual = carregar(requestId, purpose);
        long espera = settings.getResendCooldown().getSeconds() - (agora() - atual.lastSentEpochSecond());
        if (espera > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Aguarde " + espera + " s para pedir outro código.");
        }
        contarEnvio(purpose, atual.email());
        String code = gerarCodigo();
        PendingCode novo = new PendingCode(requestId, purpose, atual.email(), hash(requestId, code), 0, agora(),
            atual.decoy(), atual.payload());
        salvar(novo);
        return new Issued(novo, code);
    }

    /**
     * Confere o código. Certo: o pedido é consumido (não vale de novo) e volta com o que libera.
     * Errado: conta a tentativa; na última, o pedido é apagado.
     */
    public PendingCode verify(String requestId, VerificationPurpose purpose, String code) {
        PendingCode pending = carregar(requestId, purpose);
        String informado = code == null ? "" : code.replaceAll("\\D", "");
        boolean certo = !pending.decoy() && SecretHasher.sameHex(pending.codeHash(), hash(requestId, informado));
        if (certo) {
            store.delete(KEY_CODE + requestId);
            return pending;
        }
        int tentativas = pending.attempts() + 1;
        int restam = settings.getMaxAttempts() - tentativas;
        if (restam <= 0) {
            store.delete(KEY_CODE + requestId);
            throw new BusinessException("Código incorreto. Foram tentativas demais: peça um novo código.");
        }
        salvar(new PendingCode(pending.requestId(), purpose, pending.email(), pending.codeHash(), tentativas,
            pending.lastSentEpochSecond(), pending.decoy(), pending.payload()));
        throw new BusinessException("Código incorreto. " + (restam == 1 ? "Resta 1 tentativa." : "Restam " + restam + " tentativas."));
    }

    public long resendInSeconds() {
        return settings.getResendCooldown().getSeconds();
    }

    public long expiresInSeconds() {
        return settings.getCodeTtl().getSeconds();
    }

    private void contarEnvio(VerificationPurpose purpose, String email) {
        long envios = store.increment(KEY_SENDS + purpose + ":" + email, UMA_HORA);
        if (envios > settings.getMaxSendsPerHour()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Muitos códigos pedidos para este e-mail. Tente de novo daqui a uma hora.");
        }
    }

    private PendingCode carregar(String requestId, VerificationPurpose purpose) {
        PendingCode pending = store.get(KEY_CODE + requestId).map(this::ler)
            .filter(p -> p.purpose() == purpose)
            .orElseThrow(() -> new BusinessException("O código expirou ou já foi usado. Peça um novo código."));
        return pending;
    }

    private void salvar(PendingCode pending) {
        try {
            store.put(KEY_CODE + pending.requestId(), json.writeValueAsString(pending), settings.getCodeTtl());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao guardar o código.", e);
        }
    }

    private PendingCode ler(String valor) {
        try {
            return json.readValue(valor, PendingCode.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String hash(String requestId, String code) {
        return hasher.hmacHex("code:" + requestId + ":" + code);
    }

    private String gerarCodigo() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private long agora() {
        return clock.instant().getEpochSecond();
    }
}

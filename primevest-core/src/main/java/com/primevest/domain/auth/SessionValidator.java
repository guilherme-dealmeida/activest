package com.primevest.domain.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * O que o banco diz sobre a sessão de um token já validado pela assinatura
 * (docs/planos/PLANO_CADASTRO_USUARIO.md, D15 e D19).
 *
 * <p>Uma consulta por requisição autenticada, por chave primária: o usuário ainda existe? O token é
 * anterior à última troca de senha? Que versão dos termos ele aceitou? JDBC direto, e não o
 * repositório, para o filtro não carregar a entidade em toda chamada.</p>
 */
@Component
public class SessionValidator {

    private static final ZoneId ZONA = ZoneId.systemDefault();

    private final JdbcTemplate jdbc;

    public SessionValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Estado da conta para a sessão. {@code termsVersion} nulo = nunca aceitou. */
    public record SessionState(UUID userId, LocalDateTime passwordChangedAt, String termsVersion) {

        /**
         * Token emitido antes da troca de senha não vale. O {@code iat} do JWT tem resolução de
         * segundos, então a comparação é em segundos — por isso a troca grava o instante truncado.
         */
        public boolean accepts(Instant issuedAt) {
            if (passwordChangedAt == null) {
                return true;
            }
            long corte = passwordChangedAt.atZone(ZONA).toEpochSecond();
            return issuedAt.getEpochSecond() >= corte;
        }
    }

    public Optional<SessionState> load(UUID userId) {
        List<SessionState> rows = jdbc.query(
            "SELECT password_changed_at, terms_version FROM users WHERE id = ?",
            (rs, i) -> {
                Timestamp changed = rs.getTimestamp("password_changed_at");
                return new SessionState(userId, changed == null ? null : changed.toLocalDateTime(),
                    rs.getString("terms_version"));
            },
            userId);
        return rows.stream().findFirst();
    }
}

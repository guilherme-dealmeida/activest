package com.activest.domain.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Chave de {@link IntegrationCursor}: a conexao e o fluxo dentro dela. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IntegrationCursorId implements Serializable {

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    /** O fluxo na linguagem do conector: 'spot:BTCBRL', 'convert', 'fiat_payments'. */
    @Column(nullable = false, length = 100)
    private String stream;
}

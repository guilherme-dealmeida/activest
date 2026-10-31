package com.activest.domain.integration.source;

import com.activest.domain.integration.IntegrationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceDescriptorsTest {

    @Test
    @DisplayName("Toda fonte do enum tem descritor completo, com o logo em /images/<FONTE>.png (D9)")
    void allSources() {
        assertThat(SourceDescriptors.all()).hasSize(IntegrationProvider.values().length);
        for (SourceDescriptor d : SourceDescriptors.all()) {
            assertThat(d.name()).isNotBlank();
            assertThat(d.category()).isNotBlank();
            assertThat(d.description()).isNotBlank();
            assertThat(d.credential()).isNotNull();
            assertThat(d.logo()).isEqualTo("/images/" + d.provider().name() + ".png");
        }
    }

    @Test
    @DisplayName("Binance pede chave de API e informa depositos em reais; B3 pede o CPF")
    void credentials() {
        assertThat(SourceDescriptors.of(IntegrationProvider.BINANCE).credential())
            .isEqualTo(SourceDescriptor.CredentialKind.API_KEY);
        assertThat(SourceDescriptors.of(IntegrationProvider.BINANCE).fiatDeposits()).isTrue();
        assertThat(SourceDescriptors.of(IntegrationProvider.B3).credential())
            .isEqualTo(SourceDescriptor.CredentialKind.DOCUMENT);
        assertThat(SourceDescriptors.of(IntegrationProvider.B3).fiatDeposits()).isFalse();
        assertThat(SourceDescriptors.of(IntegrationProvider.B3).dateOnly()).isTrue();
        assertThat(SourceDescriptors.of(IntegrationProvider.BINANCE).dateOnly()).isFalse();
    }
}

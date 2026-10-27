package com.activest.domain.currency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

/**
 * As moedas do cadastro para quem não é bean: as regras de importação são puras e singletons
 * ({@code CryptoImportRules.INSTANCE}), e decidiam "isto é dinheiro, não posição" por textos fixos ("BRL", lista
 * BRL/USD/EUR). Agora perguntam aqui (docs/planos/PLANO_CADASTRO_MOEDAS.md, D12 e 6.6).
 *
 * <p>Carregado na subida do serviço e de novo quando o cadastro muda (tela de administrador). Antes da carga vale o
 * real como base e única moeda — o mesmo de antes do cadastro.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FiatCurrencies {

    private static volatile String base = Currency.BRL;
    private static volatile Set<String> codes = Set.of(Currency.BRL);

    private final CurrencyRepository currencies;

    /** Código da moeda base (a única em que a importação aceita valor, R6). */
    public static String base() {
        return base;
    }

    /** Códigos do cadastro, ativos ou não: saldo em dinheiro, não posição. */
    public static Set<String> codes() {
        return codes;
    }

    public static boolean isFiat(String code) {
        return code != null && codes.contains(code.trim().toUpperCase());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refresh() {
        Set<String> all = new TreeSet<>();
        String b = Currency.BRL;
        for (Currency c : currencies.findAll()) {
            all.add(c.getCode());
            if (c.isBase()) {
                b = c.getCode();
            }
        }
        all.add(b);
        base = b;
        codes = Set.copyOf(all);
        log.info("Moedas do cadastro: {} (base {})", codes, base);
    }
}

package com.activest.adm.integration.indices;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Respostas reais gravadas em 05/10/2026 ({@code src/test/resources/indices}). */
public final class IndexFixtures {

    private IndexFixtures() {
    }

    /** Ex.: {@code "sgs_12_2024"}, {@code "b3_IBOV_2024"}. */
    public static String body(String name) {
        try (InputStream in = IndexFixtures.class.getResourceAsStream("/indices/" + name + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("fixture inexistente: " + name);
            }
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return s.startsWith("﻿") ? s.substring(1) : s;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

package com.primevest.adm.integration.cvm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Leitor dos CSVs dos dados abertos da CVM: separador {@code ;}, ISO-8859-1,
 * primeira linha e o cabecalho. Poucas linhas trazem campo entre aspas (razao
 * social com ponto e virgula); o leitor respeita as aspas.
 *
 * <p>Le linha a linha: os arquivos de balanco passam de 100 MB e nao cabem
 * inteiros na memoria com folga.</p>
 */
public final class CvmCsv {

    public static final Charset CHARSET = Charset.forName("ISO-8859-1");

    private CvmCsv() {
    }

    /** Uma linha, com acesso pelo nome da coluna. Coluna ausente ou vazia = nulo. */
    public record Row(Map<String, Integer> header, List<String> values) {

        public String get(String column) {
            Integer i = header.get(column);
            if (i == null || i >= values.size()) {
                return null;
            }
            String v = values.get(i).trim();
            return v.isEmpty() ? null : v;
        }

        /** O primeiro nome de coluna que existir no cabecalho (os arquivos mudam de nome entre anos). */
        public String first(String... columns) {
            for (String c : columns) {
                if (header.containsKey(c)) {
                    return get(c);
                }
            }
            return null;
        }
    }

    /** Percorre as linhas do CSV. O stream nao e fechado aqui. */
    public static void forEach(InputStream in, Consumer<Row> consumer) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, CHARSET));
        try {
            String first = reader.readLine();
            if (first == null) {
                return;
            }
            Map<String, Integer> header = new HashMap<>();
            List<String> names = split(stripBom(first));
            for (int i = 0; i < names.size(); i++) {
                header.put(names.get(i).trim(), i);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    consumer.accept(new Row(header, split(line)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Row> readAll(InputStream in) {
        List<Row> rows = new ArrayList<>();
        forEach(in, rows::add);
        return rows;
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ';' && !quoted) {
                out.add(current.toString());
                current.setLength(0);
            } else if (c != '\r') {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    /** CNPJ so com digitos, 14 posicoes. Nulo se nao for um CNPJ. */
    public static String cnpj(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 14) {
            return null;
        }
        return "0".repeat(14 - digits.length()) + digits;
    }
}

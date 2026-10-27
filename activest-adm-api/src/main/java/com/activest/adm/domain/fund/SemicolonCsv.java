package com.activest.adm.domain.fund;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * CSV da CVM: ponto e vírgula, ISO-8859-1, cabeçalho na primeira linha, sem aspas. Colunas pelo nome
 * (PLANO_FUNDOS_INVESTIMENTO.md, F2).
 */
final class SemicolonCsv {

    static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    /** Uma linha, com acesso pela coluna. */
    record Row(Map<String, Integer> header, String[] fields) {
        String get(String column) {
            Integer i = header.get(column);
            if (i == null || i >= fields.length) {
                return null;
            }
            String v = fields[i].trim();
            return v.isEmpty() ? null : v;
        }
    }

    private SemicolonCsv() {
    }

    static void read(Path csv, Consumer<Row> sink) {
        try (InputStream in = new java.io.BufferedInputStream(Files.newInputStream(csv), 1 << 20)) {
            read(new BufferedReader(new InputStreamReader(in, LATIN1)), sink);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + csv.getFileName(), e);
        }
    }

    /** Os CSVs de dentro do ZIP, cada um entregue ao consumidor do seu nome (os outros são pulados). */
    static void readZip(Path zip, Map<String, Consumer<Row>> sinksByEntryName) {
        try (ZipInputStream z = new ZipInputStream(new java.io.BufferedInputStream(Files.newInputStream(zip), 1 << 20))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String name = Path.of(e.getName()).getFileName().toString();
                Consumer<Row> sink = sinksByEntryName.get(name);
                if (sink != null) {
                    read(new BufferedReader(new InputStreamReader(z, LATIN1)), sink);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler " + zip.getFileName(), ex);
        }
    }

    /** Não fecha o reader (pode ser o fluxo do ZIP). */
    static void read(BufferedReader reader, Consumer<Row> sink) throws IOException {
        String first = reader.readLine();
        if (first == null) {
            return;
        }
        String[] names = first.replace("﻿", "").split(";", -1);
        Map<String, Integer> header = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            header.put(names[i].trim(), i);
        }
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                sink.accept(new Row(header, line.split(";", -1)));
            }
        }
    }
}

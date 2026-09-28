package com.primevest.adm.integration.cvm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Leitura de um CSV de dentro dos zips anuais da CVM.
 *
 * <p>Usa {@link ZipFile} (acesso direto pela tabela do zip): o ITR tem ~500 MB
 * descompactado e o importador de balancos le mais de dez arquivos de cada zip;
 * com leitura sequencial, cada arquivo exigiria descompactar todos os anteriores.</p>
 */
public final class CvmZip {

    private CvmZip() {
    }

    /**
     * Le as linhas do arquivo do zip cujo nome e {@code entryName}, sem
     * diferenciar maiusculas (ex.: {@code fca_cia_aberta_valor_mobiliario_2026.csv}).
     * Sem o arquivo no zip, nao chama o consumidor.
     *
     * @return se o arquivo foi encontrado
     */
    public static boolean forEach(Path zip, String entryName, Consumer<CvmCsv.Row> consumer) {
        try (ZipFile file = new ZipFile(zip.toFile(), CvmCsv.CHARSET)) {
            ZipEntry entry = find(file, entryName);
            if (entry == null) {
                return false;
            }
            try (InputStream in = file.getInputStream(entry)) {
                CvmCsv.forEach(in, consumer);
            }
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Zip da CVM ilegivel: " + zip, e);
        }
    }

    /** Se o zip tem o arquivo (ex.: {@code composicao_capital} so existe de 2020 em diante). */
    public static boolean contains(Path zip, String entryName) {
        try (ZipFile file = new ZipFile(zip.toFile(), CvmCsv.CHARSET)) {
            return find(file, entryName) != null;
        } catch (IOException e) {
            throw new UncheckedIOException("Zip da CVM ilegivel: " + zip, e);
        }
    }

    private static ZipEntry find(ZipFile file, String entryName) {
        ZipEntry exact = file.getEntry(entryName);
        if (exact != null) {
            return exact;
        }
        Enumeration<? extends ZipEntry> entries = file.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.getName().equalsIgnoreCase(entryName)) {
                return e;
            }
        }
        return null;
    }
}

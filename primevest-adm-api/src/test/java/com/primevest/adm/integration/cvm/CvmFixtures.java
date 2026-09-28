package com.primevest.adm.integration.cvm;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Recortes reais dos dados abertos da CVM, gravados em 27/09/2026
 * (src/test/resources/cvm): Petrobras, Itau, Klabin, Taesa e o HGLG11.
 *
 * <p>Os CSVs ficam soltos no repositorio (legiveis, em ISO-8859-1 como na
 * fonte); o zip anual que o importador le e montado aqui, no diretorio
 * temporario do teste, com o ano pedido no nome dos arquivos.</p>
 */
public final class CvmFixtures {

    private CvmFixtures() {
    }

    public static Path resource(String name) {
        URL url = CvmFixtures.class.getResource("/cvm/" + name);
        if (url == null) {
            throw new IllegalStateException("Fixture ausente: " + name);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cadastro de companhias: copia no diretorio do teste. */
    public static Path companyRegistry(Path tempDir) {
        try {
            Path target = tempDir.resolve("cad_cia_aberta.csv");
            Files.copy(resource("cad_cia_aberta.csv"), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Zip anual montado com os CSVs da pasta {@code folder} (gravada com 2026),
     * trocando 2026 por {@code year} no nome de cada arquivo.
     */
    public static Path zip(String folder, int year, Path tempDir) {
        Path source = resource(folder);
        Path target = tempDir.resolve(folder.replace("2026", String.valueOf(year)) + ".zip");
        try (OutputStream out = Files.newOutputStream(target); ZipOutputStream zip = new ZipOutputStream(out);
             var files = Files.list(source)) {
            for (Path f : files.sorted().toList()) {
                zip.putNextEntry(new ZipEntry(f.getFileName().toString().replace("2026", String.valueOf(year))));
                Files.copy(f, zip);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target;
    }

    public static List<String> files(String folder) {
        try (var files = Files.list(resource(folder))) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

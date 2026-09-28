package com.primevest.adm.integration.b3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ISIN → CNPJ dos FIIs, pelo cadastro aberto da CVM (PLANO_BRAPI.md, A15).
 *
 * <p>O FNET busca os avisos de rendimento por CNPJ do fundo, e nem a B3 nem a
 * BRAPI devolvem o CNPJ a partir do ticker. O informe mensal dos FIIs, publico
 * em dados.cvm.gov.br, traz as duas coisas na mesma linha: {@code Codigo_ISIN}
 * (BRMXRFCTF008) e {@code CNPJ_Fundo_Classe}.</p>
 *
 * <p>O arquivo do ano (~1 MB, zip) e baixado sob demanda e fica em memoria por
 * 30 dias — so e usado quando um FII ainda nao tem CNPJ gravado.</p>
 */
@Slf4j
@Component
public class CvmFundRegistry {

    private static final Duration CACHE = Duration.ofDays(30);
    private static final Charset CVM_CHARSET = Charset.forName("ISO-8859-1");

    private final WebClient webClient;
    private final String urlPattern;
    private volatile Map<String, String> cnpjByIsin;
    private volatile Instant loadedAt;

    @Autowired
    public CvmFundRegistry(WebClient.Builder builder,
                           @Value("${cvm.fii-registry-url:https://dados.cvm.gov.br/dados/FII/DOC/INF_MENSAL/DADOS/inf_mensal_fii_%d.zip}")
                           String urlPattern) {
        this.webClient = builder.clone()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
                .build();
        this.urlPattern = urlPattern;
    }

    /** CNPJ (so digitos) do fundo com este ISIN. Nulo = nao consta no informe. */
    public String cnpjForIsin(String isin) {
        if (isin == null) {
            return null;
        }
        return registry().get(isin.trim().toUpperCase());
    }

    private synchronized Map<String, String> registry() {
        if (cnpjByIsin != null && loadedAt != null && loadedAt.plus(CACHE).isAfter(Instant.now())) {
            return cnpjByIsin;
        }
        int year = LocalDate.now().getYear();
        Map<String, String> map = new HashMap<>();
        for (int y = year; y >= year - 1 && map.isEmpty(); y--) {
            byte[] zip = download(String.format(urlPattern, y));
            if (zip != null) {
                map = parse(zip);
            }
        }
        if (map.isEmpty()) {
            throw new B3Exception("Cadastro de FIIs da CVM indisponivel");
        }
        log.info("Cadastro de FIIs da CVM carregado: {} ISINs", map.size());
        cnpjByIsin = map;
        loadedAt = Instant.now();
        return map;
    }

    private byte[] download(String url) {
        try {
            return webClient.get().uri(url).retrieve().bodyToMono(byte[].class).timeout(Duration.ofSeconds(120)).block();
        } catch (WebClientResponseException.NotFound e) {
            return null;
        } catch (Exception e) {
            throw new B3Exception("Falha ao baixar o cadastro de FIIs da CVM (" + url + "): " + e.getMessage(), e);
        }
    }

    /** Le o CSV "geral" de dentro do zip: separador ';', ISO-8859-1, com cabecalho. */
    static Map<String, String> parse(byte[] zip) {
        Map<String, String> map = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!entry.getName().contains("geral")) {
                    continue;
                }
                String[] lines = new String(in.readAllBytes(), CVM_CHARSET).split("\r?\n");
                if (lines.length == 0) {
                    break;
                }
                String[] header = lines[0].split(";", -1);
                int cnpjCol = indexOf(header, "CNPJ_Fundo_Classe", "CNPJ_Fundo");
                int isinCol = indexOf(header, "Codigo_ISIN");
                if (cnpjCol < 0 || isinCol < 0) {
                    throw new B3Exception("Informe da CVM sem as colunas de CNPJ e ISIN");
                }
                for (int i = 1; i < lines.length; i++) {
                    String[] cols = lines[i].split(";", -1);
                    if (cols.length <= Math.max(cnpjCol, isinCol)) {
                        continue;
                    }
                    String isin = cols[isinCol].trim().toUpperCase();
                    String cnpj = cols[cnpjCol].replaceAll("\\D", "");
                    if (!isin.isEmpty() && cnpj.length() == 14) {
                        map.put(isin, cnpj);
                    }
                }
            }
        } catch (IOException e) {
            throw new B3Exception("Zip da CVM ilegivel: " + e.getMessage(), e);
        }
        return map;
    }

    private static int indexOf(String[] header, String... names) {
        for (String name : names) {
            for (int i = 0; i < header.length; i++) {
                if (header[i].trim().replace("﻿", "").equalsIgnoreCase(name)) {
                    return i;
                }
            }
        }
        return -1;
    }
}

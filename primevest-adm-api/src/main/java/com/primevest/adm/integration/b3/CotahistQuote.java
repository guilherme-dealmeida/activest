package com.primevest.adm.integration.b3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Um pregao da serie historica da B3 (COTAHIST, layout posicional de 245
 * posicoes — PLANO_EMPRESAS_BALANCOS.md, 1.3).
 *
 * <p>Preco <b>nao ajustado</b> por desdobramento, que e o que o indicador precisa:
 * casa com a quantidade de acoes da mesma data. Os precos vem em centavos e
 * divididos pelo fator de cotacao ({@code FATCOT}: 1 ou 1.000).</p>
 *
 * @param market   010 = vista (lote padrao), 020 = fracionario
 * @param quantity quantidade de titulos negociados (QUATOT)
 */
public record CotahistQuote(LocalDate date, String bdi, String ticker, String market, BigDecimal open,
                            BigDecimal high, BigDecimal low, BigDecimal close, long quantity, String isin) {

    public static final String MARKET_SPOT = "010";
    public static final String MARKET_ODD_LOT = "020";
    private static final Charset CHARSET = Charset.forName("ISO-8859-1");

    /**
     * Le as linhas de pregao a vista e fracionario do zip anual (o zip tem um
     * TXT so). Opcoes, termo e demais mercados ficam de fora.
     */
    public static void forEach(Path zip, Consumer<CotahistQuote> consumer) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip), CHARSET)) {
            ZipEntry entry = in.getNextEntry();
            if (entry == null) {
                throw new UncheckedIOException(new IOException("Zip do COTAHIST vazio: " + zip));
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, CHARSET), 1 << 16);
            String line;
            while ((line = reader.readLine()) != null) {
                CotahistQuote q = parse(line);
                if (q != null) {
                    consumer.accept(q);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("COTAHIST ilegivel: " + zip, e);
        }
    }

    /** Linha de pregao a vista ou fracionario; nulo para cabecalho, rodape e outros mercados. */
    static CotahistQuote parse(String line) {
        if (line.length() < 242 || !line.startsWith("01")) {
            return null;
        }
        String market = line.substring(24, 27);
        if (!MARKET_SPOT.equals(market) && !MARKET_ODD_LOT.equals(market)) {
            return null;
        }
        long factor = Long.parseLong(line.substring(210, 217).trim());
        if (factor <= 0) {
            factor = 1;
        }
        LocalDate date = LocalDate.of(Integer.parseInt(line.substring(2, 6)),
                Integer.parseInt(line.substring(6, 8)), Integer.parseInt(line.substring(8, 10)));
        return new CotahistQuote(date, line.substring(10, 12), line.substring(12, 24).trim(), market,
                price(line, 56, factor), price(line, 69, factor), price(line, 82, factor),
                price(line, 108, factor), Long.parseLong(line.substring(152, 170).trim()),
                line.substring(230, 242).trim());
    }

    private static BigDecimal price(String line, int start, long factor) {
        long cents = Long.parseLong(line.substring(start, start + 13).trim());
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100L * factor), 8, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }
}

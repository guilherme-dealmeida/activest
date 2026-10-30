package com.activest.adm.domain.foreign;

import com.activest.domain.asset.ForeignExchange;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Um Form 25 / 25-NSE lido (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C6): a classe que saiu da bolsa, a bolsa, o SIC
 * da empresa e a data de saída. O 25-NSE (da bolsa) traz um XML ({@code notificationOfRemoval}); o 25 (da empresa) é um
 * HTML livre, lido pelo texto.
 *
 * @param effective a saída: a data do anexo da bolsa ("at the opening of business on ...") ou o arquivamento + 10 dias
 */
public record Form25(String accession, String formType, String cik, String company, LocalDate filed,
                     LocalDate effective, String sic, String exchangeName, ForeignExchange exchange,
                     String securityClass) {

    private static final Pattern SIC = Pattern.compile("STANDARD INDUSTRIAL CLASSIFICATION:[^\\[\\n]*\\[(\\d{3,4})\\]");
    private static final Pattern CLASS_XML = Pattern.compile(
            "<descriptionClassSecurity>(.*?)</descriptionClassSecurity>", Pattern.DOTALL);
    private static final Pattern EXCHANGE_XML = Pattern.compile("<exchange>.*?<entityName>(.*?)</entityName>",
            Pattern.DOTALL);
    private static final Pattern ISSUER_CIK = Pattern.compile("<issuer>\\s*<cik>0*(\\d{1,10})</cik>");
    private static final Pattern OPENING = Pattern.compile(
            "opening of (?:business|trading) on\\s+([A-Z][a-z]+\\.?\\s+\\d{1,2},\\s+\\d{4})");
    private static final Pattern CLASS_TEXT = Pattern.compile("\\(\\s*Description of class of securities\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_NODE = Pattern.compile(">([^<>]+)<");
    private static final Pattern EXCHANGE_TEXT = Pattern.compile(
            "Nasdaq|NYSE\\s*Arca|NYSE\\s*American|NYSE\\s*MKT|NYSE\\s*Amex|American Stock Exchange|New York Stock Exchange|"
                    + "\\bNYSE\\b|Cboe|BATS|Investors Exchange", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter US = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);

    /** Lê o arquivo completo do formulário ({@code edgar/data/{cik}/{acesso}.txt}). */
    public static Form25 parse(String text, String accession, String formType, String cik, String company,
                               LocalDate filed) {
        String sic = find(SIC, text);
        String issuer = find(ISSUER_CIK, text);
        if (issuer != null) {
            // o 25-NSE aparece no índice também com o CIK da bolsa: vale o emissor do XML
            cik = issuer;
        }
        String cls = find(CLASS_XML, text);
        String exchangeName = find(EXCHANGE_XML, text);
        String plain = null;
        if (cls == null || exchangeName == null) {
            plain = text.replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ").replace("&#160;", " ")
                    .replaceAll("\\s+", " ");
            if (cls == null) {
                cls = classFromHtml(text);
            }
            if (exchangeName == null) {
                // a bolsa citada no corpo, fora do cabeçalho da SEC
                int body = plain.indexOf("FORM 25");
                Matcher m = EXCHANGE_TEXT.matcher(body >= 0 ? plain.substring(body) : plain);
                exchangeName = m.find() ? m.group() : null;
            }
        }
        LocalDate effective = filed.plusDays(10);
        Matcher m = OPENING.matcher(plain != null ? plain : text.replaceAll("\\s+", " "));
        if (m.find()) {
            try {
                effective = LocalDate.parse(m.group(1).replace(".", "").replaceAll("\\s+", " "), US);
            } catch (DateTimeParseException ignored) {
                // fica o arquivamento + 10 dias
            }
        }
        return new Form25(accession, formType, cik, company, filed, effective, sic, clean(exchangeName),
                exchange(exchangeName), clean(cls));
    }

    /** O último texto antes de "(Description of class of securities)" no HTML do Form 25 da empresa. */
    static String classFromHtml(String html) {
        Matcher label = CLASS_TEXT.matcher(html);
        if (!label.find()) {
            return null;
        }
        String before = html.substring(Math.max(0, label.start() - 3000), label.start());
        Matcher node = TEXT_NODE.matcher(before);
        String last = null;
        while (node.find()) {
            String t = node.group(1).replace("&nbsp;", " ").replace("&#160;", " ").replaceAll("\\s+", " ").trim();
            if (!t.isEmpty() && !t.startsWith("(")) {
                last = t;
            }
        }
        return last;
    }

    /** A bolsa pelo nome (as da D1); nula fora delas. */
    public static ForeignExchange exchange(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toUpperCase(Locale.ROOT);
        if (n.contains("ARCA")) {
            return ForeignExchange.NYSE_ARCA;
        }
        if (n.contains("NASDAQ")) {
            return ForeignExchange.NASDAQ;
        }
        if (n.contains("AMERICAN") || n.contains("MKT") || n.contains("AMEX")) {
            return ForeignExchange.NYSE_AMERICAN;
        }
        if (n.contains("NEW YORK STOCK EXCHANGE") || n.matches(".*\\bNYSE\\b.*")) {
            return ForeignExchange.NYSE;
        }
        if (n.contains("CBOE") || n.contains("BATS")) {
            return ForeignExchange.CBOE;
        }
        if (n.contains("INVESTORS EXCHANGE") || n.matches(".*\\bIEX\\b.*")) {
            return ForeignExchange.IEX;
        }
        return null;
    }

    private static String find(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String c = s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replaceAll("\\s+", " ").trim();
        return c.isEmpty() ? null : c;
    }
}

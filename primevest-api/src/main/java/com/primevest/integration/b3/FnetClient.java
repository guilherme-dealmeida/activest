package com.primevest.integration.b3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fundos.NET (FNET) — os avisos de rendimentos e amortizacoes publicados pelo
 * administrador de cada FII (PLANO_BRAPI.md, 5.9 e B15).
 *
 * <p>E a fonte dos proventos de FII alem dos 12 meses que a B3 devolve: o
 * "Aviso aos Cotistas - Estruturado" (categoria 14, tipo 41) e um XML com
 * ISIN, ticker, data-base (a data-com), valor por cota, data de pagamento,
 * periodo de referencia e se e isento de IR. Existe de 2017 em diante; antes
 * disso o aviso era PDF, e o periodo fica para lancamento manual.</p>
 *
 * <p>A busca e por CNPJ do fundo (ver {@link CvmFundRegistry}).</p>
 */
@Component
public class FnetClient {

    private static final int PAGE = 100;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    @Autowired
    public FnetClient(WebClient.Builder builder,
                      @Value("${fnet.base-url:https://fnet.bmfbovespa.com.br/fnet/publico}") String baseUrl,
                      @Value("${fnet.timeout-seconds:60}") long timeoutSeconds,
                      ObjectMapper objectMapper) {
        this.webClient = builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /** Ids dos avisos de rendimentos e amortizacoes de um fundo, todas as paginas. */
    public List<Long> incomeNoticeIds(String cnpj) {
        List<Long> ids = new ArrayList<>();
        for (int start = 0; start < 5000; start += PAGE) {
            final int s = start;
            String body = get(uri -> uri.path("/pesquisarGerenciadorDocumentosDados")
                    .queryParam("d", 1)
                    .queryParam("s", s)
                    .queryParam("l", PAGE)
                    .queryParam("cnpjFundo", cnpj)
                    .queryParam("idCategoriaDocumento", 14)
                    .queryParam("idTipoDocumento", 41)
                    .queryParam("idEspecieDocumento", 0)
                    .build());
            JsonNode data;
            try {
                data = objectMapper.readTree(body).path("data");
            } catch (Exception e) {
                throw new B3Exception("FNET respondeu fora do formato esperado: " + e.getMessage(), e);
            }
            if (!data.isArray() || data.isEmpty()) {
                break;
            }
            data.forEach(d -> {
                if (d.hasNonNull("id")) {
                    ids.add(d.get("id").asLong());
                }
            });
            if (data.size() < PAGE) {
                break;
            }
        }
        return ids;
    }

    /** Os proventos de um aviso. */
    public List<FnetProvento> incomeNotice(long id) {
        String xml = get(uri -> uri.path("/downloadDocumento").queryParam("id", id).build());
        return parse(xml);
    }

    /** Le o XML do aviso estruturado. Publico para os testes com arquivo real. */
    public static List<FnetProvento> parse(String xml) {
        List<FnetProvento> result = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return result;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            // O download costuma vir como XML; alguns documentos vem em base64
            // (entre aspas). Os dois casos caem aqui.
            String clean = xml.replace("\uFEFF", "").strip();
            if (!clean.startsWith("<")) {
                clean = new String(java.util.Base64.getMimeDecoder().decode(clean.replace("\"", "")),
                        StandardCharsets.UTF_8).replace("\uFEFF", "").strip();
            }
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(clean.getBytes(StandardCharsets.UTF_8)));
            NodeList proventos = doc.getElementsByTagName("Provento");
            for (int i = 0; i < proventos.getLength(); i++) {
                Element provento = (Element) proventos.item(i);
                String isin = text(provento, "CodISIN");
                String ticker = text(provento, "CodNegociacao");
                NodeList children = provento.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (!(child instanceof Element item) || item.getElementsByTagName("DataBase").getLength() == 0) {
                        continue;
                    }
                    var value = B3Values.decimal(text(item, "ValorProvento"));
                    var dataBase = B3Values.date(text(item, "DataBase"));
                    if (value == null || value.signum() <= 0 || dataBase == null) {
                        continue;
                    }
                    boolean amortization = item.getTagName().toLowerCase().startsWith("amortiza");
                    String exempt = firstTextContaining(item, "Isent");
                    result.add(new FnetProvento(isin, ticker, amortization, dataBase, value,
                            B3Values.date(text(item, "DataPagamento")), text(item, "PeriodoReferencia"),
                            exempt == null ? null : exempt.trim().equalsIgnoreCase("Sim")));
                }
            }
        } catch (Exception e) {
            throw new B3Exception("Aviso do FNET ilegivel: " + e.getMessage(), e);
        }
        return result;
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String t = nodes.item(0).getTextContent();
        return t == null || t.isBlank() ? null : t.trim();
    }

    private static String firstTextContaining(Element parent, String fragment) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element e && e.getTagName().contains(fragment)) {
                return e.getTextContent();
            }
        }
        return null;
    }

    private String get(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
        try {
            String body = webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(timeout).block();
            if (body == null) {
                throw new B3Exception("FNET respondeu vazio");
            }
            return body;
        } catch (B3Exception e) {
            throw e;
        } catch (Exception e) {
            throw new B3Exception("Falha ao consultar o FNET: " + e.getMessage(), e);
        }
    }
}

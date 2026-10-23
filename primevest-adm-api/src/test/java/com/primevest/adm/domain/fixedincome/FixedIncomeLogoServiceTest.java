package com.primevest.adm.domain.fixedincome;

import com.primevest.domain.fixedincome.FixedIncomeLogos;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logos de renda fixa por raiz de CNPJ (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, Adendo A): Open Finance primeiro,
 * logos-bancos-br depois; SVG com script nao entra; fonte fora do ar nao derruba a outra. As duas fontes servidas por
 * um HTTP local com o formato real delas.
 */
class FixedIncomeLogoServiceTest {

    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">"
        + "<rect width=\"10\" height=\"10\" fill=\"#820ad1\"/></svg>";
    private static final String SVG_COM_SCRIPT = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\">"
        + "<script>alert(1)</script><rect width=\"10\" height=\"10\"/></svg>";
    private static final byte[] PNG = new byte[80];

    @TempDir
    Path dir;

    private HttpServer server;
    private final Map<String, String[]> routes = new HashMap<>();
    private String base;

    @BeforeEach
    void startUp() throws Exception {
        PNG[0] = (byte) 0x89;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String[] r = routes.get(ex.getRequestURI().getPath());
            byte[] body = r == null ? new byte[0]
                : "image/png".equals(r[0]) ? PNG : r[1].getBytes(StandardCharsets.UTF_8);
            if (r != null) {
                ex.getResponseHeaders().add("Content-Type", r[0]);
            }
            ex.sendResponseHeaders(r == null ? 404 : 200, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void goDown() {
        server.stop(0);
    }

    private FixedIncomeLogoService service(FixedIncomeLogos logos) {
        return new FixedIncomeLogoService(null, null, logos, WebClient.builder(),
            base + "/participants", base + "/lbb/");
    }

    private void sources() {
        // Open Finance: Nu Financeira com SVG limpo; 11111111 com SVG que tem script
        routes.put("/participants", new String[] {"application/json", """
            [{"RegistrationNumber":"30680829000143","AuthorisationServers":[
               {"CustomerFriendlyLogoUri":"%1$s/of/nu.svg"}]},
             {"RegistrationNumber":"11111111000100","AuthorisationServers":[
               {"CustomerFriendlyLogoUri":"%1$s/of/script.svg"}]},
             {"RegistrationNumber":"33333333000100","AuthorisationServers":[]}]
            """.formatted(base)});
        routes.put("/of/nu.svg", new String[] {"image/svg+xml", SVG});
        routes.put("/of/script.svg", new String[] {"image/svg+xml", SVG_COM_SCRIPT});
        // logos-bancos-br: ISPB -> arquivo (o indice aponta o SVG da versao @0; vale o PNG da versao fixa)
        routes.put("/lbb/data/logo-urls.min.json", new String[] {"application/json", """
            {"00000000":"https://cdn.jsdelivr.net/npm/logos-bancos-br@0/logos/svg/00000000.svg",
             "11111111":"https://cdn.jsdelivr.net/npm/logos-bancos-br@0/logos/svg/11111111.svg",
             "44444444":"https://cdn.jsdelivr.net/npm/logos-bancos-br@0/logos/svg/00000000.svg"}
            """});
        routes.put("/lbb/logos/png/00000000.png", new String[] {"image/png", ""});
        routes.put("/lbb/logos/png/11111111.png", new String[] {"image/png", ""});
    }

    @Test
    @DisplayName("Open Finance primeiro; SVG com script cai para o PNG do logos-bancos-br; sem fonte, sem arquivo")
    void sourcesOrder() {
        sources();
        FixedIncomeLogos logos = new FixedIncomeLogos(dir.toString());

        FixedIncomeLogoService.Result r = service(logos)
            .syncRoots(List.of("30680829", "11111111", "00000000", "44444444", "22222222"), true, "teste");

        assertThat(r.openFinance()).isEqualTo(1);
        assertThat(r.logosBancosBr()).isEqualTo(3);
        Path rf = dir.resolve("rf");
        assertThat(rf.resolve("30680829.svg")).exists();
        assertThat(rf.resolve("11111111.svg")).doesNotExist();
        assertThat(rf.resolve("11111111.png")).exists();
        // ISPB que o pacote liga ao logo de outro (mesmo grupo): grava o arquivo com a raiz pedida
        assertThat(rf.resolve("44444444.png")).exists();
        assertThat(rf.resolve("22222222.png")).doesNotExist();
        assertThat(logos.hasLogo("30680829")).isTrue();
    }

    @Test
    @DisplayName("Rotina mensal: quem ja tem arquivo nao e baixado de novo")
    void onlyMissing() throws Exception {
        sources();
        Path rf = Files.createDirectories(dir.resolve("rf"));
        Files.writeString(rf.resolve("30680829.png"), "logo curado");
        FixedIncomeLogos logos = new FixedIncomeLogos(dir.toString());

        FixedIncomeLogoService.Result r = service(logos).syncRoots(List.of("30680829"), true, "teste");

        assertThat(r.candidates()).isZero();
        assertThat(Files.readString(rf.resolve("30680829.png"))).isEqualTo("logo curado");
    }

    @Test
    @DisplayName("Open Finance fora do ar: o logos-bancos-br segue sozinho")
    void sourceDown() {
        sources();
        routes.remove("/participants");
        FixedIncomeLogos logos = new FixedIncomeLogos(dir.toString());

        FixedIncomeLogoService.Result r = service(logos).syncRoots(List.of("30680829", "00000000"), true, "teste");

        assertThat(r.openFinance()).isZero();
        assertThat(r.logosBancosBr()).isEqualTo(1);
        assertThat(dir.resolve("rf").resolve("00000000.png")).exists();
    }

    @Test
    @DisplayName("PNG com margem transparente: a margem sai e a marca fica centrada num quadrado")
    void cutsMargin() throws Exception {
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.GREEN);
        g.fillRect(30, 40, 40, 20);
        g.dispose();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bytes);

        java.awt.image.BufferedImage out = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(
            FixedIncomeLogoService.trimTransparentMargin(bytes.toByteArray())));

        assertThat(out.getWidth()).isEqualTo(40);
        assertThat(out.getHeight()).isEqualTo(40);
        assertThat(out.getRGB(20, 20) >>> 24).isEqualTo(255);
        assertThat(out.getRGB(20, 5) >>> 24).isZero();
        // nao e PNG legivel: volta como veio
        assertThat(FixedIncomeLogoService.trimTransparentMargin(PNG)).isSameAs(PNG);
    }

    @Test
    @DisplayName("SVG seguro: sem script, evento ou conteudo embutido")
    void safeSvgFile() {
        assertThat(FixedIncomeLogoService.safeSvg(SVG.getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(FixedIncomeLogoService.safeSvg(SVG_COM_SCRIPT.getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(FixedIncomeLogoService.safeSvg("<svg onload=\"x()\"/>".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(FixedIncomeLogoService.safeSvg("<svg><a href=\"javascript:x\"/></svg>".getBytes(StandardCharsets.UTF_8)))
            .isFalse();
    }
}

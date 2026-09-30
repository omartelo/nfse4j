package io.github.omartelo.nfse4j.danfse;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Item 2.2.2 da NT 008/2026 v1.02: as margens entre o corpo impresso e o final do formulario
 * devem ter no minimo 0,15 cm e no maximo 0,20 cm, inclusive superior e inferior.
 */
class MargensTest {

    private static final int DPI = 200;
    private static final double MIN_CM = 0.15;
    private static final double MAX_CM = 0.20;
    /** Um pixel a 200 DPI vale 0,0127 cm; a folga cobre arredondamento da rasterizacao. */
    private static final double TOLERANCIA_CM = 0.04;

    private static boolean temTinta(int rgb) {
        return ((((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3) < 240;
    }

    /** Margens em cm na ordem: superior, inferior, esquerda, direita. */
    private static double[] margens(byte[] pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, DPI);
            int topo = -1;
            int base = -1;
            int esq = -1;
            int dir = -1;
            for (int y = 0; y < img.getHeight() && topo < 0; y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if (temTinta(img.getRGB(x, y))) {
                        topo = y;
                        break;
                    }
                }
            }
            for (int y = img.getHeight() - 1; y >= 0 && base < 0; y--) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if (temTinta(img.getRGB(x, y))) {
                        base = y;
                        break;
                    }
                }
            }
            for (int x = 0; x < img.getWidth() && esq < 0; x++) {
                for (int y = 0; y < img.getHeight(); y++) {
                    if (temTinta(img.getRGB(x, y))) {
                        esq = x;
                        break;
                    }
                }
            }
            for (int x = img.getWidth() - 1; x >= 0 && dir < 0; x--) {
                for (int y = 0; y < img.getHeight(); y++) {
                    if (temTinta(img.getRGB(x, y))) {
                        dir = x;
                        break;
                    }
                }
            }
            double f = 2.54 / DPI;
            return new double[] {
                topo * f,
                (img.getHeight() - 1 - base) * f,
                esq * f,
                (img.getWidth() - 1 - dir) * f
            };
        }
    }

    /**
     * Altura, em cm, do ultimo quadro do documento (Informacoes Complementares): vai da ultima
     * linha divisoria ate a borda inferior da moldura. E esse quadro que absorve a altura
     * liberada quando um bloco e suprimido (itens 2.3.1 a 2.3.3).
     */
    private static double alturaDoUltimoQuadroCm(byte[] pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, DPI);
            int largura = img.getWidth();
            int divisoria = -1;
            int borda = -1;
            for (int y = img.getHeight() - 1; y > 10; y--) {
                int escuros = 0;
                int amostras = 0;
                for (int x = 30; x < largura - 30; x += 3) {
                    if (temTinta(img.getRGB(x, y))) {
                        escuros++;
                    }
                    amostras++;
                }
                if (borda < 0 && escuros > 0) {
                    borda = y;
                } else if (borda >= 0 && divisoria < 0 && y < borda - 10
                        && escuros > amostras * 0.8) {
                    divisoria = y;
                }
            }
            return (borda - divisoria) * (2.54 / DPI);
        }
    }

    @org.junit.jupiter.api.Test
    void suprimirUmBlocoAumentaOUltimoQuadroEmVezDaMargem() throws Exception {
        // Itens 2.3.1 a 2.3.3: a altura liberada por um bloco suprimido deve ir para o quadro de
        // Descricao do Servico e/ou Informacoes Complementares. Como o ultimo quadro e o de
        // Informacoes Complementares, ele deve crescer exatamente quando um bloco some, e a
        // margem inferior nao pode se mexer.
        String base;
        try (var in = MargensTest.class.getResourceAsStream("/nfse-exemplo-ficticio.xml")) {
            base = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        byte[] comTomador = DanfseGenerator.gerarPdf(base);
        byte[] semTomador = DanfseGenerator.gerarPdf(base.replaceAll("(?s)<toma>.*?</toma>", ""));

        double quadroCom = alturaDoUltimoQuadroCm(comTomador);
        double quadroSem = alturaDoUltimoQuadroCm(semTomador);
        assertTrue(quadroSem > quadroCom + 0.3,
            String.format("o quadro final deveria absorver a altura do bloco suprimido: %.2f -> %.2f cm",
                quadroCom, quadroSem));

        // O minimo do quadro no item 2.4.5 e 0,39 cm; e a margem nao muda com o conteudo.
        assertTrue(quadroCom >= 0.39, String.format("quadro final com %.2f cm", quadroCom));
        assertTrue(Math.abs(margens(semTomador)[1] - margens(comTomador)[1]) <= TOLERANCIA_CM,
            "a margem inferior nao pode variar com os blocos preenchidos");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "/nfse-exemplo-ficticio.xml", "/nfse-exemplo-ibscbs.xml", "/nfse-exemplo-completo.xml"})
    void margensDentroDoIntervaloDaNt(String recurso) throws Exception {
        String xml;
        try (var in = MargensTest.class.getResourceAsStream(recurso)) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        double[] m = margens(DanfseGenerator.gerarPdf(xml));
        String[] nomes = {"superior", "inferior", "esquerda", "direita"};
        for (int i = 0; i < m.length; i++) {
            assertTrue(m[i] >= MIN_CM - TOLERANCIA_CM && m[i] <= MAX_CM + TOLERANCIA_CM,
                String.format("margem %s = %.2f cm, a NT exige entre %.2f e %.2f cm",
                    nomes[i], m[i], MIN_CM, MAX_CM));
        }
    }
}

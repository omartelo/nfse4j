package io.github.omartelo.nfse4j.danfse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tamanho do simbolo do QR no PDF: o item 2.4.3 da NT 008/2026 v1.02 exige no minimo
 * 1,52 x 1,52 cm.
 *
 * <p>Mede dentro do PDF (matriz de transformacao da imagem e modulos escuros do bitmap) porque
 * a comparacao do {@link GoldenPdfTest} tolera ate 0,8% de pixels divergentes, e uma mudanca
 * localizada como esta passa por baixo desse limiar.
 */
class QrGeometriaTest {

    private static final double PT_CM = 2.54 / 72.0;
    private static final double MINIMO_CM = 1.52;

    private record ImagemPdf(double larguraPt, double alturaPt, BufferedImage bitmap) {
    }

    private static final class Coletor extends PDFStreamEngine {
        final List<ImagemPdf> imagens = new ArrayList<>();

        Coletor() {
            addOperator(new Save());
            addOperator(new Restore());
            addOperator(new Concatenate());
            addOperator(new SetGraphicsStateParameters());
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.get(0) instanceof COSName nome
                    && getResources().getXObject(nome) instanceof PDImageXObject img) {
                Matrix m = getGraphicsState().getCurrentTransformationMatrix();
                imagens.add(new ImagemPdf(m.getScalingFactorX(), m.getScalingFactorY(), img.getImage()));
                return;
            }
            super.processOperator(operator, operands);
        }
    }

    private static ImagemPdf localizarQr(PDDocument doc) throws IOException {
        Coletor coletor = new Coletor();
        coletor.processPage(doc.getPage(0));
        for (ImagemPdf img : coletor.imagens) {
            double l = img.larguraPt() * PT_CM;
            double a = img.alturaPt() * PT_CM;
            if (Math.abs(l - a) < 0.05 && l > 1.0 && l < 3.0) {
                return img;
            }
        }
        return null;
    }

    @ParameterizedTest(name = "tpAmb={0}")
    @ValueSource(strings = {"1", "2"})
    void simboloDoQrTemNoMinimo1_52cm(String tpAmb) throws Exception {
        // A URL muda de comprimento entre producao e homologacao, o que muda a versao do QR e o
        // numero de modulos. O simbolo tem que atingir o minimo nos dois casos.
        String xml = NfseXmlReaderTest.xmlExemplo().replace("<tpAmb>2</tpAmb>", "<tpAmb>" + tpAmb + "</tpAmb>");
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(DanfseGenerator.gerarPdf(xml)))) {
            ImagemPdf qr = localizarQr(doc);
            assertNotNull(qr, "QR nao encontrado na pagina");

            int minX = qr.bitmap().getWidth();
            int maxX = -1;
            for (int y = 0; y < qr.bitmap().getHeight(); y++) {
                for (int x = 0; x < qr.bitmap().getWidth(); x++) {
                    int rgb = qr.bitmap().getRGB(x, y);
                    if (((((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3) < 128) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                    }
                }
            }
            double fracaoEscura = (maxX - minX + 1) / (double) qr.bitmap().getWidth();
            // O bitmap nao pode trazer quiet zone nem padding: se trouxer, o tamanho fixado no CSS
            // vale para a imagem inteira e o simbolo sai menor que o minimo.
            assertEquals(1.0, fracaoEscura, 0.001, "o bitmap do QR deve conter apenas o simbolo");

            double simboloCm = qr.larguraPt() * PT_CM * fracaoEscura;
            // 1,52 cm vem de uma declaracao exata no CSS: a folga cobre so arredondamento de pt.
            assertTrue(simboloCm >= MINIMO_CM - 0.01,
                String.format("simbolo escuro do QR mede %.3f cm, minimo da NT e %.2f cm",
                    simboloCm, MINIMO_CM));
        }
    }
}

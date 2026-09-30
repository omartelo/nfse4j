package io.github.omartelo.nfse4j.danfse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class QrCodeGeneratorTest {

    private static final String CHAVE = "35503082212345678000199000000000001234500000000000001";

    private static BufferedImage decodificar(String dataUri) throws Exception {
        String base64 = dataUri.substring(dataUri.indexOf(',') + 1);
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }

    /** Menor retangulo que contem modulos escuros, em pixels: {minX, minY, maxX, maxY}. */
    private static int[] caixaEscura(BufferedImage img) {
        int minX = img.getWidth();
        int minY = img.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                if (((((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3) < 128) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    @Test
    void bitmapContemApenasOSimbolo() throws Exception {
        // NT 008, item 2.4.3: o minimo de 1,52 cm vale para o simbolo. Se o bitmap trouxer quiet
        // zone ou padding, o tamanho fixado no CSS se aplica a area maior e o simbolo fica menor.
        for (boolean producao : new boolean[] {true, false}) {
            BufferedImage img = decodificar(
                QrCodeGenerator.dataUri(QrCodeGenerator.consultaUrl(CHAVE, producao), 300));
            int[] bb = caixaEscura(img);
            assertEquals(0, bb[0], "borda esquerda sem margem branca (producao=" + producao + ")");
            assertEquals(0, bb[1], "borda superior sem margem branca (producao=" + producao + ")");
            assertEquals(img.getWidth() - 1, bb[2], "borda direita (producao=" + producao + ")");
            assertEquals(img.getHeight() - 1, bb[3], "borda inferior (producao=" + producao + ")");
        }
    }

    @Test
    void bitmapEQuadradoEProximoDoTamanhoPedido() throws Exception {
        BufferedImage img = decodificar(
            QrCodeGenerator.dataUri(QrCodeGenerator.consultaUrl(CHAVE, true), 300));
        assertEquals(img.getWidth(), img.getHeight(), "o simbolo do QR e quadrado");
        assertTrue(img.getWidth() >= 200 && img.getWidth() <= 300,
            "escala inteira de modulos proxima de 300 px; veio " + img.getWidth());
    }

    @Test
    void qrContinuaLegivelAposMudancaDeEscala() throws Exception {
        // Garante que o bitmap ainda decodifica para a URL de consulta.
        BufferedImage img = decodificar(
            QrCodeGenerator.dataUri(QrCodeGenerator.consultaUrl(CHAVE, true), 300));
        int[] pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        var fonte = new com.google.zxing.RGBLuminanceSource(img.getWidth(), img.getHeight(), pixels);
        var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(fonte));
        var resultado = new com.google.zxing.qrcode.QRCodeReader().decode(bitmap);
        assertEquals(QrCodeGenerator.consultaUrl(CHAVE, true), resultado.getText());
    }
}

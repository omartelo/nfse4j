package io.github.omartelo.nfse4j.core.http;

import com.google.gson.annotations.SerializedName;
import io.github.omartelo.nfse4j.core.xml.XmlPayloadCodec;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resposta da distribuicao de DF-e do ADN ({@code LoteDistribuicaoNSUResponse}). E o mesmo corpo no
 * HTTP 200 e nas respostas de negocio 400/404; o que separa os casos e o {@link #statusProcessamento()}.
 *
 * <p>Lote vazio e rejeicao sao coisas diferentes: {@link StatusProcessamento#NENHUM_DOCUMENTO_LOCALIZADO}
 * encerra a drenagem, {@link StatusProcessamento#REJEICAO} e erro, com o detalhe em {@link #erros()}.
 * Um status que esta versao nao conhece chega como {@code null}.
 */
public record LoteDistribuicaoDfe(StatusProcessamento statusProcessamento,
                                  @SerializedName("LoteDFe") List<Documento> loteDfe,
                                  List<Mensagem> alertas, List<Mensagem> erros) {

    public LoteDistribuicaoDfe {
        loteDfe = loteDfe == null ? List.of() : List.copyOf(loteDfe);
        alertas = alertas == null ? List.of() : List.copyOf(alertas);
        erros = erros == null ? List.of() : List.copyOf(erros);
    }

    public String descreverErros() {
        return erros.stream()
            .map(erro -> erro.codigo() + ": " + erro.descricao())
            .collect(Collectors.joining("; "));
    }

    public enum StatusProcessamento {
        DOCUMENTOS_LOCALIZADOS,
        NENHUM_DOCUMENTO_LOCALIZADO,
        REJEICAO
    }

    /**
     * Documento do lote. A chave de acesso da NFS-e tem 50 digitos. {@code tipoDocumento} fica como
     * texto (NFSE, EVENTO, DPS, CNC...) para um tipo novo do ADN nao virar {@code null} em silencio.
     */
    public record Documento(@SerializedName("NSU") long nsu, String chaveAcesso, String tipoDocumento,
                            String tipoEvento, String arquivoXml, String dataHoraGeracao) {

        /** XML do documento; o ADN entrega {@code arquivoXml} em gzip+base64. */
        public String xml() {
            return XmlPayloadCodec.ungzipBase64(arquivoXml);
        }
    }

    public record Mensagem(String codigo, String descricao, String complemento) {
    }
}

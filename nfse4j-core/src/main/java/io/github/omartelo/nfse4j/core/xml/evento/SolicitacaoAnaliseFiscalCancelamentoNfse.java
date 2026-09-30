package io.github.omartelo.nfse4j.core.xml.evento;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Evento 101103: pede ao municipio a analise fiscal do cancelamento, usado quando o cancelamento
 * direto (101101) nao e mais aceito. Codigos de motivo (TSCodJustAnaliseFiscalCanc): 1 erro na
 * emissao, 2 servico nao prestado, 9 outros.
 */
public record SolicitacaoAnaliseFiscalCancelamentoNfse(
    String chaveAcesso,
    String cpfCnpjAutor,
    OffsetDateTime dataHoraEvento,
    String codigoMotivo,
    String descricaoMotivo,
    String versaoAplicativo
) implements PedidoRegistroEvento {
    public static final String TIPO_EVENTO = "101103";
    public static final String DESCRICAO_EVENTO = "Solicitação de Análise Fiscal para Cancelamento de NFS-e";

    public SolicitacaoAnaliseFiscalCancelamentoNfse {
        Objects.requireNonNull(dataHoraEvento, "dataHoraEvento is required");
        CamposPedidoEvento.exigirPreenchido(chaveAcesso, "Chave de acesso da NFS-e e obrigatoria.");
        cpfCnpjAutor = CamposPedidoEvento.normalizarAutor(cpfCnpjAutor);
        CamposPedidoEvento.exigirPreenchido(codigoMotivo, "Codigo do motivo da solicitacao e obrigatorio.");
        CamposPedidoEvento.exigirPreenchido(descricaoMotivo, "Descricao do motivo da solicitacao e obrigatoria.");
        CamposPedidoEvento.exigirPreenchido(versaoAplicativo, "Versao do aplicativo e obrigatoria.");
    }

    @Override
    public String tipoEvento() {
        return TIPO_EVENTO;
    }
}

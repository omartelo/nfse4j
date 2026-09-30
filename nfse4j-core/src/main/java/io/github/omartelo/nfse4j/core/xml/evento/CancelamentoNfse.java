package io.github.omartelo.nfse4j.core.xml.evento;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Evento 101101. {@code numeroPedido} permanece por compatibilidade de API, mas nao compoe mais o Id
 * do pedido (ver {@link PedidoRegistroEvento#idPedidoRegistroEvento()}).
 */
public record CancelamentoNfse(
    String chaveAcesso,
    String cpfCnpjAutor,
    OffsetDateTime dataHoraEvento,
    int numeroPedido,
    String codigoMotivo,
    String descricaoMotivo,
    String versaoAplicativo
) implements PedidoRegistroEvento {
    public static final String TIPO_EVENTO = "101101";
    public static final String DESCRICAO_EVENTO = "Cancelamento de NFS-e";

    public CancelamentoNfse {
        Objects.requireNonNull(chaveAcesso, "chaveAcesso is required");
        Objects.requireNonNull(dataHoraEvento, "dataHoraEvento is required");
        Objects.requireNonNull(codigoMotivo, "codigoMotivo is required");
        Objects.requireNonNull(descricaoMotivo, "descricaoMotivo is required");
        Objects.requireNonNull(versaoAplicativo, "versaoAplicativo is required");

        CamposPedidoEvento.exigirPreenchido(chaveAcesso, "Chave de acesso da NFS-e e obrigatoria.");
        cpfCnpjAutor = CamposPedidoEvento.normalizarAutor(cpfCnpjAutor);
        if (numeroPedido < 1 || numeroPedido > 999) {
            throw new IllegalArgumentException("Numero do pedido de evento deve estar entre 1 e 999.");
        }
        CamposPedidoEvento.exigirPreenchido(codigoMotivo, "Codigo do motivo de cancelamento e obrigatorio.");
        CamposPedidoEvento.exigirPreenchido(descricaoMotivo, "Descricao do motivo de cancelamento e obrigatoria.");
        CamposPedidoEvento.exigirPreenchido(versaoAplicativo, "Versao do aplicativo e obrigatoria.");
    }

    @Override
    public String tipoEvento() {
        return TIPO_EVENTO;
    }
}

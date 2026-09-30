package io.github.omartelo.nfse4j.core.xml.evento;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Manifestacao de NFS-e (confirmacao ou rejeicao). Rejeicao exige {@code codigoMotivo}
 * (TSCodMotivoRejeicao: 1 duplicidade, 2 ja emitida pelo tomador, 3 fato gerador nao ocorreu,
 * 4 erro de responsabilidade tributaria, 5 erro de valor/servico/data, 9 outros) e aceita
 * {@code descricaoMotivo} opcional. Confirmacao nao aceita motivo.
 */
public record ManifestacaoNfse(
    String chaveAcesso,
    String cpfCnpjAutor,
    OffsetDateTime dataHoraEvento,
    TipoManifestacao tipo,
    String codigoMotivo,
    String descricaoMotivo,
    String versaoAplicativo
) implements PedidoRegistroEvento {

    public ManifestacaoNfse {
        Objects.requireNonNull(dataHoraEvento, "dataHoraEvento is required");
        Objects.requireNonNull(tipo, "tipo is required");
        CamposPedidoEvento.exigirPreenchido(chaveAcesso, "Chave de acesso da NFS-e e obrigatoria.");
        cpfCnpjAutor = CamposPedidoEvento.normalizarAutor(cpfCnpjAutor);
        CamposPedidoEvento.exigirPreenchido(versaoAplicativo, "Versao do aplicativo e obrigatoria.");
        if (tipo.rejeicao()) {
            CamposPedidoEvento.exigirPreenchido(codigoMotivo, "Codigo do motivo da rejeicao e obrigatorio.");
        } else if (CamposPedidoEvento.preenchido(codigoMotivo) || CamposPedidoEvento.preenchido(descricaoMotivo)) {
            throw new IllegalArgumentException("Confirmacao de NFS-e nao aceita motivo.");
        }
    }

    @Override
    public String tipoEvento() {
        return tipo.tipoEvento();
    }
}

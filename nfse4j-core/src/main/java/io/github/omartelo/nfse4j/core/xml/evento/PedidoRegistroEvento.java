package io.github.omartelo.nfse4j.core.xml.evento;

import java.time.OffsetDateTime;

/**
 * Pedido de registro de evento (pedRegEvento) que o contribuinte pode enviar para uma NFS-e.
 * Cada implementacao gera um grupo eXXXXXX do TCInfPedReg (tiposEventos v1.01).
 */
public sealed interface PedidoRegistroEvento
    permits CancelamentoNfse, SolicitacaoAnaliseFiscalCancelamentoNfse, ManifestacaoNfse {

    String chaveAcesso();

    String cpfCnpjAutor();

    OffsetDateTime dataHoraEvento();

    String versaoAplicativo();

    String tipoEvento();

    // A partir de jan/2026 o nPedRegEvento foi REMOVIDO do Id do pedido de evento.
    // TSIdPedRegEvt agora exige pattern "PRE[0-9]{56}" (maxLength 59): "PRE" + chave de
    // acesso (50) + tipo do evento (6). Anexar o numero do pedido gerava 62 chars e era rejeitado
    // pela SEFIN com E1235 (falha no esquema XML).
    default String idPedidoRegistroEvento() {
        return "PRE" + chaveAcesso() + tipoEvento();
    }

    default boolean autorPessoaJuridica() {
        return cpfCnpjAutor().length() == 14;
    }
}

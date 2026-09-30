package io.github.omartelo.nfse4j.core.service;

import io.github.omartelo.nfse4j.core.NfseContext;
import io.github.omartelo.nfse4j.core.http.NfseHttpResponse;
import io.github.omartelo.nfse4j.core.http.SefinClient;
import io.github.omartelo.nfse4j.core.xml.XmlSchemaValidationException;
import io.github.omartelo.nfse4j.core.xml.XmlSchemaValidator;
import io.github.omartelo.nfse4j.core.xml.XmlSchemaViolation;
import io.github.omartelo.nfse4j.core.xml.XmlSigner;
import io.github.omartelo.nfse4j.core.xml.dps.Dps;
import io.github.omartelo.nfse4j.core.xml.dps.DpsXmlBuilder;
import io.github.omartelo.nfse4j.core.xml.evento.CancelamentoNfse;
import io.github.omartelo.nfse4j.core.xml.evento.PedidoRegistroEventoXmlBuilder;
import java.util.List;

public final class ContribuinteService {
    private final NfseContext context;
    private final SefinClient sefinClient;
    private final DpsXmlBuilder dpsXmlBuilder;
    private final PedidoRegistroEventoXmlBuilder eventoXmlBuilder;

    public ContribuinteService(NfseContext context) {
        this.context = context;
        this.sefinClient = new SefinClient(context);
        this.dpsXmlBuilder = new DpsXmlBuilder();
        this.eventoXmlBuilder = new PedidoRegistroEventoXmlBuilder();
    }

    public String consultar(String chaveAcesso) {
        return consultarNfse(chaveAcesso).body();
    }

    public NfseHttpResponse emitir(Dps dps) {
        return emitirDetalhado(dps).response();
    }

    public EmissaoNfseResult emitirDetalhado(Dps dps) {
        Dps dpsNoAmbiente = comTipoAmbienteDoContexto(dps);
        String xml = dpsXmlBuilder.build(dpsNoAmbiente);
        String signedXml = XmlSigner.signInfDps(
            xml,
            context.certificado()
                .orElseThrow(() -> new ContribuinteServiceException("Certificado A1 e obrigatorio para emitir DPS."))
        );
        exigirConformeXsd("DPS", XmlSchemaValidator.validarDps(signedXml));
        NfseHttpResponse response = sefinClient.emitirNfseXml(signedXml);
        return new EmissaoNfseResult(response, xml, signedXml);
    }

    // O tpAmb da DPS sempre segue o ambiente do contexto (= o endpoint usado). Sem isso,
    // um tpAmb divergente do endpoint faz a SEFIN rejeitar com E0006.
    private Dps comTipoAmbienteDoContexto(Dps dps) {
        int tpAmb = context.ambiente().tipoAmbiente();
        if (dps.infDps().tipoAmbiente() == tpAmb) {
            return dps;
        }
        return new Dps(dps.versao(), dps.infDps().withTipoAmbiente(tpAmb));
    }

    public NfseHttpResponse emitirXml(String dpsXml) {
        exigirConformeXsd("DPS", XmlSchemaValidator.validarDps(dpsXml));
        return sefinClient.emitirNfseXml(dpsXml);
    }

    public NfseHttpResponse cancelar(CancelamentoNfse cancelamento) {
        String xml = eventoXmlBuilder.buildCancelamento(context.ambiente().tipoAmbiente(), cancelamento);
        String signedXml = XmlSigner.signElement(
            xml,
            "infPedReg",
            context.certificado()
                .orElseThrow(() -> new ContribuinteServiceException("Certificado A1 e obrigatorio para cancelar NFS-e."))
        );
        return sefinClient.registrarEventoXml(cancelamento.chaveAcesso(), signedXml);
    }

    public NfseHttpResponse consultarNfse(String chaveAcesso) {
        return sefinClient.consultarNfse(chaveAcesso);
    }

    public NfseHttpResponse consultarDps(String idDps) {
        return sefinClient.consultarDps(idDps);
    }

    public boolean verificarDps(String idDps) {
        return sefinClient.verificarDps(idDps);
    }

    public NfseHttpResponse registrarEventoXml(String chaveAcesso, String eventoXml) {
        return sefinClient.registrarEventoXml(chaveAcesso, eventoXml);
    }

    // Valida o XML ja assinado, que e exatamente o que vai para a SEFIN: a ds:Signature e opcional
    // no XSD, mas quando presente tambem e conferida contra o xmldsig-core-schema.
    private static void exigirConformeXsd(String documento, List<XmlSchemaViolation> violacoes) {
        if (!violacoes.isEmpty()) {
            throw new XmlSchemaValidationException(documento, violacoes);
        }
    }
}

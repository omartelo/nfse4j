package io.github.omartelo.nfse4j.core.xml.dps;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record Dps(String versao, InfDps infDps) {

    public Dps {
        Objects.requireNonNull(versao, "versao is required");
        Objects.requireNonNull(infDps, "infDps is required");
    }

    public record InfDps(
        String id,
        int tipoAmbiente,
        OffsetDateTime dataHoraEmissao,
        String versaoAplicativo,
        String serie,
        long numero,
        LocalDate dataCompetencia,
        int tipoEmitente,
        String codigoLocalEmissao,
        Prestador prestador,
        Tomador tomador,
        Servico servico,
        Valores valores,
        Substituicao substituicao
    ) {
        public InfDps(
            String id,
            int tipoAmbiente,
            OffsetDateTime dataHoraEmissao,
            String versaoAplicativo,
            String serie,
            long numero,
            LocalDate dataCompetencia,
            int tipoEmitente,
            String codigoLocalEmissao,
            Prestador prestador,
            Tomador tomador,
            Servico servico,
            Valores valores
        ) {
            this(id, tipoAmbiente, dataHoraEmissao, versaoAplicativo, serie, numero, dataCompetencia, tipoEmitente,
                codigoLocalEmissao, prestador, tomador, servico, valores, null);
        }

        /** Copia trocando apenas o tpAmb. Usado pela SDK para casar o XML com o ambiente do endpoint. */
        public InfDps withTipoAmbiente(int novoTipoAmbiente) {
            return new InfDps(
                id, novoTipoAmbiente, dataHoraEmissao, versaoAplicativo, serie, numero,
                dataCompetencia, tipoEmitente, codigoLocalEmissao, prestador, tomador, servico, valores, substituicao
            );
        }
    }

    /**
     * subst: a DPS substitui a NFS-e {@code chaveSubstituida}; a SEFIN cancela a original gerando o evento
     * 105102. Dominios do TCSubstituicao (XSD DPS v1.01): chave com 50 digitos, codigo 01-05 ou 99 e
     * descricao opcional de 15 a 255 caracteres.
     *
     * @throws IllegalArgumentException se algum campo sair do dominio do XSD
     */
    public record Substituicao(String chaveSubstituida, String codigoMotivo, String descricaoMotivo) {
        private static final List<String> CODIGOS_MOTIVO = List.of("01", "02", "03", "04", "05", "99");
        private static final int DESCRICAO_MOTIVO_MIN = 15;
        private static final int DESCRICAO_MOTIVO_MAX = 255;

        public Substituicao {
            if (chaveSubstituida == null || !chaveSubstituida.matches("[0-9]{50}")) {
                throw new IllegalArgumentException(
                    "chaveSubstituida deve ter 50 digitos (recebido: " + chaveSubstituida + ").");
            }
            if (codigoMotivo == null || !CODIGOS_MOTIVO.contains(codigoMotivo)) {
                throw new IllegalArgumentException("codigoMotivo da substituicao deve ser um de: "
                    + String.join(", ", CODIGOS_MOTIVO) + " (recebido: " + codigoMotivo + ").");
            }
            if (descricaoMotivo != null) {
                exigirDescricaoMotivoNoDominio(descricaoMotivo);
            }
        }

        private static void exigirDescricaoMotivoNoDominio(String descricaoMotivo) {
            if (descricaoMotivo.isBlank()) {
                throw new IllegalArgumentException(
                    "descricaoMotivo da substituicao nao pode ficar em branco; omita o campo se nao houver descricao.");
            }
            int tamanho = descricaoMotivo.length();
            if (tamanho < DESCRICAO_MOTIVO_MIN || tamanho > DESCRICAO_MOTIVO_MAX) {
                throw new IllegalArgumentException("descricaoMotivo da substituicao deve ter de "
                    + DESCRICAO_MOTIVO_MIN + " a " + DESCRICAO_MOTIVO_MAX + " caracteres (recebido: " + tamanho + ").");
            }
        }
    }

    public record Prestador(
        String cnpj,
        String cpf,
        String nif,
        Integer codigoNaoNif,
        String caepf,
        String inscricaoMunicipal,
        String nome,
        Endereco endereco,
        String telefone,
        String email,
        RegimeTributario regimeTributario
    ) {
        public Prestador(
            String cnpj,
            String cpf,
            String inscricaoMunicipal,
            String telefone,
            String email,
            RegimeTributario regimeTributario
        ) {
            this(cnpj, cpf, null, null, null, inscricaoMunicipal, null, null, telefone, email, regimeTributario);
        }

        public Prestador(String cnpj, String cpf, String telefone, String email, RegimeTributario regimeTributario) {
            this(cnpj, cpf, null, telefone, email, regimeTributario);
        }
    }

    public record RegimeTributario(
        Integer opcaoSimplesNacional,
        Integer regimeApuracaoSimplesNacional,
        Integer regimeEspecialTributacao
    ) {
        public RegimeTributario(Integer opcaoSimplesNacional, Integer regimeEspecialTributacao) {
            this(opcaoSimplesNacional, null, regimeEspecialTributacao);
        }
    }

    /** TCInfoPessoa do XSD: identificado por CNPJ, CPF, NIF ou cNaoNIF (motivo de nao informar o NIF). */
    public record Tomador(
        String cnpj,
        String cpf,
        String nif,
        Integer codigoNaoNif,
        String caepf,
        String inscricaoMunicipal,
        String nome,
        Endereco endereco,
        String telefone,
        String email
    ) {
        public Tomador(String cnpj, String cpf, String nome, Endereco endereco, String telefone, String email) {
            this(cnpj, cpf, null, null, null, null, nome, endereco, telefone, email);
        }
    }

    /** Endereco nacional (cMun e CEP) ou no exterior; o XSD aceita so um dos dois. */
    public record Endereco(
        String codigoMunicipio,
        String cep,
        EnderecoExterior exterior,
        String logradouro,
        String numero,
        String complemento,
        String bairro
    ) {
        public Endereco(
            String codigoMunicipio,
            String cep,
            String logradouro,
            String numero,
            String complemento,
            String bairro
        ) {
            this(codigoMunicipio, cep, null, logradouro, numero, complemento, bairro);
        }
    }

    /** endExt: pais (ISO 3166-1 alfa-2), codigo postal, cidade e estado/provincia/regiao. */
    public record EnderecoExterior(String codigoPais, String codigoEnderecamentoPostal, String cidade, String estadoProvinciaRegiao) {
    }

    public record Servico(
        String codigoLocalPrestacao,
        String codigoTributacaoNacional,
        String codigoTributacaoMunicipal,
        String descricao,
        String codigoNbs,
        InformacoesComplementares informacoesComplementares
    ) {
        public Servico(
            String codigoLocalPrestacao,
            String codigoTributacaoNacional,
            String codigoTributacaoMunicipal,
            String descricao,
            String codigoNbs
        ) {
            this(codigoLocalPrestacao, codigoTributacaoNacional, codigoTributacaoMunicipal, descricao, codigoNbs, null);
        }

        public Servico(String codigoLocalPrestacao, String codigoTributacaoNacional, String descricao) {
            this(codigoLocalPrestacao, codigoTributacaoNacional, null, descricao, null);
        }
    }

    /**
     * infoCompl: por ora so o xInfComp, texto livre que o DANFSe mostra em "Informacoes complementares".
     * Dominio do TSDescInfCompl (XSD DPS v1.01): 1 a 2000 caracteres no padrao do TSString, isto e, so
     * de U+0020 a U+00FF, sem quebra de linha e sem espaco no inicio ou no fim.
     *
     * @throws IllegalArgumentException se o texto sair do dominio do XSD
     */
    public record InformacoesComplementares(String texto) {
        private static final int TEXTO_MAX = 2000;
        // Pattern do TSString em tiposSimples_v1.01.xsd, com o [!-ÿ] do XSD escrito como U+0021-U+00FF.
        private static final Pattern TEXTO_PERMITIDO =
            Pattern.compile("[!-\\u00FF][ -\\u00FF]*[!-\\u00FF]|[!-\\u00FF]");

        public InformacoesComplementares {
            if (texto == null || texto.isBlank()) {
                throw new IllegalArgumentException(
                    "texto das informacoes complementares nao pode ficar em branco; omita o grupo se nao houver texto.");
            }
            if (texto.length() > TEXTO_MAX) {
                throw new IllegalArgumentException("texto das informacoes complementares deve ter ate "
                    + TEXTO_MAX + " caracteres (recebido: " + texto.length() + ").");
            }
            if (!TEXTO_PERMITIDO.matcher(texto).matches()) {
                throw new IllegalArgumentException("texto das informacoes complementares aceita so caracteres de "
                    + "U+0020 a U+00FF (Latin-1), sem quebra de linha e sem espaco no inicio ou no fim.");
            }
        }
    }

    public record Valores(
        BigDecimal valorServico,
        Tributacao tributacao,
        BigDecimal valorRecebido,
        Descontos descontos,
        DeducaoReducao deducaoReducao
    ) {
        public Valores(BigDecimal valorServico, Tributacao tributacao) {
            this(valorServico, tributacao, null, null, null);
        }
    }

    /** vDescCondIncond: descontos incondicionado e condicionado, em R$. */
    public record Descontos(BigDecimal incondicionado, BigDecimal condicionado) {
    }

    /** vDedRed por percentual (pDR), por valor (vDR) ou por documentos; o XSD aceita so uma das tres formas. */
    public record DeducaoReducao(BigDecimal percentual, BigDecimal valor, List<DocumentoDeducao> documentos) {
        public DeducaoReducao {
            documentos = documentos == null ? null : List.copyOf(documentos);
        }

        public DeducaoReducao(BigDecimal percentual, BigDecimal valor) {
            this(percentual, valor, null);
        }
    }

    /**
     * docDedRed: documento que embasa a deducao/reducao, identificado por um so entre chave de NFS-e,
     * chave de NF-e, NFS-e municipal, NF/NFS, numero de documento fiscal ou numero de documento. O
     * fornecedor segue o mesmo tipo do tomador (TCInfoPessoa no XSD).
     */
    public record DocumentoDeducao(
        String chaveNfse,
        String chaveNfe,
        NfseMunicipal nfseMunicipal,
        NotaFiscalServico notaFiscalServico,
        String numeroDocumentoFiscal,
        String numeroDocumento,
        Integer tipo,
        String descricaoOutraDeducao,
        LocalDate dataEmissao,
        BigDecimal valorDedutivelRedutivel,
        BigDecimal valorDeducaoReducao,
        Tomador fornecedor
    ) {
    }

    /** NFSeMun: NFS-e emitida fora do Sistema Nacional. */
    public record NfseMunicipal(String codigoMunicipio, String numero, String codigoVerificacao) {
    }

    /** NFNFS: nota fiscal ou nota fiscal de servico em papel. */
    public record NotaFiscalServico(String numero, String modelo, String serie) {
    }

    public record Tributacao(
        Integer tributacaoIssqn,
        Integer tipoRetencaoIssqn,
        Integer indicadorTotalTributos,
        BigDecimal percentualTotalTributosSimplesNacional,
        TotalTributos valorTotalTributos,
        TotalTributos percentualTotalTributos,
        String codigoPaisResultado,
        Integer tipoImunidade,
        ExigibilidadeSuspensa exigibilidadeSuspensa,
        BeneficioMunicipal beneficioMunicipal,
        BigDecimal aliquotaIssqn,
        TributacaoFederal tributacaoFederal
    ) {
        public Tributacao {
            var informados = new ArrayList<String>();
            if (valorTotalTributos != null) {
                informados.add("valorTotalTributos");
            }
            if (percentualTotalTributos != null) {
                informados.add("percentualTotalTributos");
            }
            if (indicadorTotalTributos != null) {
                informados.add("indicadorTotalTributos");
            }
            if (percentualTotalTributosSimplesNacional != null) {
                informados.add("percentualTotalTributosSimplesNacional");
            }
            if (informados.size() > 1) {
                throw new IllegalArgumentException(
                    "totTrib aceita um so grupo (choice do XSD da DPS); vieram juntos: " + String.join(", ", informados));
            }
        }

        public Tributacao(Integer tributacaoIssqn, Integer tipoRetencaoIssqn, Integer indicadorTotalTributos) {
            this(tributacaoIssqn, tipoRetencaoIssqn, indicadorTotalTributos, null);
        }

        public Tributacao(
            Integer tributacaoIssqn,
            Integer tipoRetencaoIssqn,
            Integer indicadorTotalTributos,
            BigDecimal percentualTotalTributosSimplesNacional
        ) {
            this(tributacaoIssqn, tipoRetencaoIssqn, indicadorTotalTributos, percentualTotalTributosSimplesNacional, null, null);
        }

        public Tributacao(
            Integer tributacaoIssqn,
            Integer tipoRetencaoIssqn,
            Integer indicadorTotalTributos,
            BigDecimal percentualTotalTributosSimplesNacional,
            TotalTributos valorTotalTributos,
            TotalTributos percentualTotalTributos
        ) {
            this(tributacaoIssqn, tipoRetencaoIssqn, indicadorTotalTributos, percentualTotalTributosSimplesNacional,
                valorTotalTributos, percentualTotalTributos, null, null, null, null, null, null);
        }
    }

    /** exigSusp: tipo da suspensao da exigibilidade do ISSQN e numero do processo. */
    public record ExigibilidadeSuspensa(Integer tipo, String numeroProcesso) {
    }

    /** BM: beneficio municipal com reducao da base de calculo por valor (vRedBCBM) ou percentual (pRedBCBM). */
    public record BeneficioMunicipal(
        String numero,
        BigDecimal valorReducaoBaseCalculo,
        BigDecimal percentualReducaoBaseCalculo
    ) {
    }

    /** tribFed: PIS/COFINS e retencoes de CP, IRRF e CSLL. */
    public record TributacaoFederal(
        PisCofins pisCofins,
        BigDecimal valorRetidoCp,
        BigDecimal valorRetidoIrrf,
        BigDecimal valorRetidoCsll
    ) {
    }

    public record PisCofins(
        String cst,
        BigDecimal baseCalculo,
        BigDecimal aliquotaPis,
        BigDecimal aliquotaCofins,
        BigDecimal valorPis,
        BigDecimal valorCofins,
        Integer tipoRetencao
    ) {
    }

    /** Total aproximado dos tributos por esfera (Lei 12.741/2012): em R$ no vTotTrib, em % no pTotTrib. */
    public record TotalTributos(BigDecimal federal, BigDecimal estadual, BigDecimal municipal) {
    }
}

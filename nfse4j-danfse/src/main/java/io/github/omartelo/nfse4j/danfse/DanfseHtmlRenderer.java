package io.github.omartelo.nfse4j.danfse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renderiza o {@link Danfse} em XHTML (bem-formado, para o OpenHTMLtoPDF) seguindo o modelo
 * oficial do DANFSe (Anexo I da NT 008/2026 v1.02): cabecalho com logomarca/titulo/municipio e
 * ambiente, identificacao com QR Code de 1,52 cm, blocos Prestador, Tomador, Destinatario,
 * Intermediario, Servico Prestado, Tributacao Municipal (ISSQN), Tributacao Federal, IBS/CBS,
 * Valor Total e Informacoes Complementares (com os Totais Aproximados dos Tributos obrigatorios).
 *
 * <p>Regras aplicadas: campos ausentes viram "-" (Nota 12); blocos de tomador/destinatario/
 * intermediario ausentes viram a linha unica "NAO IDENTIFICADO NA NFS-e" (Notas 2 e 3); textos
 * excedentes recebem reticencias; fontes minimas de 6/7pt conforme item 2.4.
 */
public final class DanfseHtmlRenderer {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private DanfseHtmlRenderer() {
    }

    public static String render(Danfse d, String qrDataUri) {
        return render(d, qrDataUri, null, DanfseConfig.vazio());
    }

    public static String render(Danfse d, String qrDataUri, String logoDataUri, DanfseConfig config) {
        DanfseConfig cfg = config == null ? DanfseConfig.vazio() : config;
        StringBuilder h = new StringBuilder(16384);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        h.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>");
        h.append("<style>").append(css()).append("</style></head><body><div class=\"frame\">");

        cabecalho(h, d, logoDataUri, cfg);
        identificacao(h, d, qrDataUri);
        pessoa(h, "PRESTADOR / FORNECEDOR", d.prestador(), true, true,
            "PRESTADOR/FORNECEDOR NÃO IDENTIFICADO NA NFS-e");
        pessoa(h, "TOMADOR / ADQUIRENTE", d.tomador(), false, true,
            "TOMADOR/ADQUIRENTE DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e");
        destinatario(h, d);
        pessoa(h, "INTERMEDIÁRIO DA OPERAÇÃO", d.intermediario(), false, true,
            "INTERMEDIÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e");
        servico(h, d.servico());
        tributacaoMunicipal(h, d.tributacaoMunicipal());
        tributacaoFederal(h, d.tributacaoFederal(), d.identificacao());
        ibsCbs(h, d.ibsCbs());
        valorTotal(h, d.valores(), d.ibsCbs());
        informacoesComplementares(h, d);

        h.append("</div></body></html>");
        return h.toString();
    }

    // ---- cabecalho (2.4.3) ----

    private static void cabecalho(StringBuilder h, Danfse d, String logoDataUri, DanfseConfig cfg) {
        Danfse.Identificacao id = d.identificacao();
        h.append("<table class=\"hdr\"><tr>");

        h.append("<td class=\"hdr-l\">");
        if (logoDataUri != null) {
            h.append("<img class=\"logo\" src=\"").append(logoDataUri).append("\" alt=\"NFS-e\"/>");
        } else {
            h.append("<span class=\"tit\">NFS-e</span>");
        }
        if (cfg.temLogoEmitente()) {
            h.append("<img class=\"logo-emit\" src=\"").append(cfg.logoEmitenteDataUri()).append("\" alt=\"\"/>");
        }
        h.append("</td>");

        h.append("<td class=\"hdr-c\">")
            .append("<div class=\"tit\">DANFSe v2.0</div>")
            .append("<div class=\"tit\">Documento Auxiliar da NFS-e</div>");
        if (d.homologacao()) {
            h.append("<div class=\"aviso\">NFS-e SEM VALIDADE JURÍDICA</div>");
        }
        h.append("</td>");

        h.append("<td class=\"hdr-r\">");
        // Municipio do emitente. A NT manda ocultar quando o item do codigo de tributacao
        // nacional for 99 (exterior/casos especiais).
        boolean ocultarMunicipio = d.servico() != null
            && d.servico().codigoTributacaoNacional() != null
            && d.servico().codigoTributacaoNacional().startsWith("99");
        String municipio = cfg.municipioNome() != null && !cfg.municipioNome().isBlank()
            ? cfg.municipioNome()
            : municipioEmissao(d);
        if (!ocultarMunicipio) {
            h.append("<div class=\"mun\">");
            if (cfg.temBrasao()) {
                h.append("<img class=\"brasao\" src=\"").append(cfg.brasaoDataUri()).append("\" alt=\"\"/>");
            }
            h.append("Município: ").append(esc(dashText(municipio))).append("</div>");
        }
        h.append("<div class=\"amb\">Ambiente Gerador: ")
            .append(esc(dashText(id == null ? null : id.ambienteGerador()))).append("</div>")
            .append("<div class=\"amb\">Tipo de Ambiente: ")
            .append(esc(dashText(id == null ? null : id.tipoAmbiente()))).append("</div>");
        if (cfg.temContato()) {
            StringBuilder c = new StringBuilder();
            juntar(c, cfg.departamento());
            juntar(c, cfg.telefone());
            juntar(c, cfg.email());
            h.append("<div class=\"amb\">").append(esc(c.toString())).append("</div>");
        }
        h.append("</td>");

        h.append("</tr></table>");
    }

    private static String municipioEmissao(Danfse d) {
        Danfse.Identificacao id = d.identificacao();
        if (id == null || id.municipioEmissao() == null) {
            return null;
        }
        String uf = d.prestador() != null && d.prestador().endereco() != null
            ? d.prestador().endereco().uf()
            : null;
        return uf == null ? id.municipioEmissao() : id.municipioEmissao() + " - " + uf;
    }

    // ---- identificacao (2.1.2) com QR (1,52 cm) ----

    private static void identificacao(StringBuilder h, Danfse d, String qrDataUri) {
        Danfse.Identificacao id = d.identificacao();
        h.append("<table class=\"grid\">");

        h.append("<tr>");
        h.append("<td colspan=\"3\"><div class=\"lblid\">CHAVE DE ACESSO DA NFS-E</div><div class=\"val\">")
            .append(dash(d.chaveAcesso())).append("</div></td>");
        h.append("<td class=\"qrcell\" rowspan=\"4\">");
        if (qrDataUri != null) {
            h.append("<img class=\"qr\" src=\"").append(qrDataUri).append("\" alt=\"QR\"/>");
        }
        h.append("<div class=\"qrtxt\">A autenticidade desta NFS-e pode ser verificada pela leitura ")
            .append("deste código QR ou pela consulta da chave de acesso no portal nacional da NFS-e</div>")
            .append("</td>");
        h.append("</tr>");

        row(h,
            campoId("NÚMERO DA NFS-E", dash(id == null ? null : id.numeroNfse())),
            campoId("COMPETÊNCIA DA NFS-E", data(id == null ? null : id.competencia())),
            campoId("DATA E HORA DA EMISSÃO DA NFS-E", dataHora(id == null ? null : id.emissaoNfse())));
        row(h,
            campoId("NÚMERO DA DPS", dash(id == null ? null : id.numeroDps())),
            campoId("SÉRIE DA DPS", dash(id == null ? null : id.serieDps())),
            campoId("DATA E HORA DA EMISSÃO DA DPS", dataHora(id == null ? null : id.emissaoDps())));
        row(h,
            campoIdSombreado("EMITENTE DA NFS-e", dash(id == null ? null : id.emitente())),
            campoId("SITUAÇÃO DA NFS-e", dash(resumo(id == null ? null : id.situacao(), 40))),
            campoId("FINALIDADE", dash(resumo(id == null ? null : id.finalidade(), 40))));

        h.append("</table>");
    }

    // ---- blocos de pessoa (2.1.3 a 2.1.6) ----

    private static void pessoa(StringBuilder h, String titulo, Danfse.Pessoa p,
            boolean prestador, boolean temInscricao, String linhaAusente) {
        if (p == null) {
            bandline(h, linhaAusente);
            return;
        }
        h.append("<table class=\"grid\">");
        row(h,
            btitle(titulo),
            campo("CNPJ / CPF / NIF", documento(p)),
            temInscricao
                ? campo("Indicador Municipal (Inscrição)", dash(p.inscricaoMunicipal()))
                : campo("", ""),
            campo("Telefone", telefone(p.telefone())));
        row(h,
            campoSpan("Nome / Nome Empresarial", dash(resumo(p.nome(), 77)), 2),
            campo("Município / Sigla UF", municipioPessoa(p)),
            campo("Código IBGE / CEP", codigoIbgeCep(p)));
        row(h,
            campoSpan("Endereço", endereco(p), 2),
            campoSpan("E-mail", dash(p.email()), 2));
        if (prestador) {
            row(h,
                campoSpan("Simples Nacional na Data de Competência", dash(resumo(p.regimeSimplesNacional(), 40)), 2),
                campoSpan("Regime de Apuração Tributária pelo SN", dash(resumo(p.regimeApuracaoSimplesNacional(), 77)), 2));
        }
        h.append("</table>");
    }

    private static void destinatario(StringBuilder h, Danfse d) {
        Danfse.Pessoa dest = d.destinatario();
        if (dest == null) {
            bandline(h, "DESTINATÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e");
            return;
        }
        if (mesmaPessoa(dest, d.tomador())) {
            bandline(h, "O DESTINATÁRIO É O PRÓPRIO TOMADOR/ADQUIRENTE DA OPERAÇÃO");
            return;
        }
        pessoa(h, "DESTINATÁRIO DA OPERAÇÃO", dest, false, false,
            "DESTINATÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e");
    }

    private static boolean mesmaPessoa(Danfse.Pessoa a, Danfse.Pessoa b) {
        if (a == null || b == null) {
            return false;
        }
        String docA = primeiro(a.cnpj(), a.cpf(), a.nif());
        String docB = primeiro(b.cnpj(), b.cpf(), b.nif());
        return docA != null && docA.equals(docB);
    }

    // ---- servico prestado (2.1.7) ----

    private static void servico(StringBuilder h, Danfse.Servico s) {
        h.append("<table class=\"grid\">");
        row(h,
            btitle("SERVIÇO PRESTADO"),
            campo("Código de Tributação Nacional / Municipal", codigoTribNacMun(s)),
            campo("Código da NBS", nbs(s)),
            campo("Local da Prestação / Sigla UF / País", localPrestacao(s)));
        // Descricao do codigo de tributacao: SEM label (2.4.5); municipal quando presente, senao nacional.
        String descTrib = s == null ? null
            : (s.descricaoTributacaoMunicipal() != null && !s.descricaoTributacaoMunicipal().isBlank()
                ? s.descricaoTributacaoMunicipal()
                : s.descricaoTributacaoNacional());
        h.append("<tr><td colspan=\"4\"><div class=\"val\">").append(dash(resumo(descTrib, 167)))
            .append("</div></td></tr>");
        h.append("<tr><td colspan=\"4\" class=\"descserv\"><div class=\"lbl\">Descrição do Serviço</div><div class=\"val\">")
            .append(dash(resumo(s == null ? null : s.descricao(), 1297))).append("</div></td></tr>");
        h.append("</table>");
    }

    private static String codigoTribNacMun(Danfse.Servico s) {
        if (s == null || (s.codigoTributacaoNacional() == null && s.codigoTributacaoMunicipal() == null)) {
            return "-";
        }
        String nac = formatoTribNac(s.codigoTributacaoNacional());
        return (nac == null ? "-" : esc(nac)) + " / "
            + (s.codigoTributacaoMunicipal() == null ? "-" : esc(s.codigoTributacaoMunicipal()));
    }

    /** 010101 -> 01.01.01, como no modelo (nn.nn.nn). */
    private static String formatoTribNac(String c) {
        if (c == null) {
            return null;
        }
        String digitos = c.replaceAll("\\D", "");
        if (digitos.length() != 6) {
            return c;
        }
        return digitos.substring(0, 2) + "." + digitos.substring(2, 4) + "." + digitos.substring(4);
    }

    /** 123456789 -> 1.2345.67.89, como no modelo (n.nnnn.nn.nn). */
    private static String nbs(Danfse.Servico s) {
        if (s == null || s.codigoNbs() == null) {
            return "-";
        }
        String digitos = s.codigoNbs().replaceAll("\\D", "");
        if (digitos.length() != 9) {
            return esc(s.codigoNbs());
        }
        return digitos.substring(0, 1) + "." + digitos.substring(1, 5) + "." + digitos.substring(5, 7)
            + "." + digitos.substring(7);
    }

    private static String localPrestacao(Danfse.Servico s) {
        if (s == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        juntarBarra(sb, s.localPrestacao());
        juntarBarra(sb, MunicipioResolver.uf(s.codigoLocalPrestacao()));
        juntarBarra(sb, s.paisPrestacao());
        return sb.length() == 0 ? "-" : esc(sb.toString());
    }

    // ---- tributacao municipal (2.1.8) ----

    private static void tributacaoMunicipal(StringBuilder h, Danfse.TributacaoMunicipal t) {
        if (t == null) {
            bandline(h, "TRIBUTAÇÃO MUNICIPAL (ISSQN) - OPERAÇÃO NÃO SUJEITA AO ISSQN");
            return;
        }
        h.append("<table class=\"grid\">");
        row(h,
            btitle("TRIBUTAÇÃO MUNICIPAL (ISSQN)"),
            campo("Tipo de Tributação do ISSQN", dash(resumo(t.tipoTributacaoIssqn(), 21))),
            campoSpan("Município / Sigla UF / País de Incidência do ISSQN", municipioIncidencia(t), 2));
        // Nota 5: linhas suprimiveis quando TODOS os campos da linha estao vazios no XML.
        if (temAlgum(t.regimeEspecial(), t.tipoImunidade(), t.suspensaoExigibilidade(), t.numeroProcessoSuspensao())) {
            row(h,
                campo("Regime Especial de Tributação do ISSQN", dash(resumo(t.regimeEspecial(), 27))),
                campo("Tipo de Imunidade do ISSQN", dash(resumo(t.tipoImunidade(), 37))),
                campo("Suspensão da Exigibilidade do ISSQN", dash(resumo(t.suspensaoExigibilidade(), 37))),
                campo("Número Processo Suspensão", dash(t.numeroProcessoSuspensao())));
        }
        if (t.beneficioMunicipal() != null || t.calculoBeneficioMunicipal() != null
                || t.totalDeducoesReducoes() != null || t.descontoIncondicionado() != null) {
            row(h,
                campo("Benefício Municipal", dash(t.beneficioMunicipal())),
                campo("Cálculo do BM", money(t.calculoBeneficioMunicipal())),
                campo("Total Deduções/Reduções", money(t.totalDeducoesReducoes())),
                campo("Desconto Incondicionado", money(t.descontoIncondicionado())));
        }
        row(h,
            campo("BC ISSQN", money(t.baseCalculo())),
            campo("Alíquota Aplicada", percent(t.aliquotaAplicada())),
            campo("Retenção do ISSQN", dash(t.retencaoIssqn())),
            campo("ISSQN Apurado", money(t.issqnApurado())));
        h.append("</table>");
    }

    private static String municipioIncidencia(Danfse.TributacaoMunicipal t) {
        StringBuilder sb = new StringBuilder();
        juntarBarra(sb, t.municipioIncidencia());
        juntarBarra(sb, MunicipioResolver.uf(t.codigoMunicipioIncidencia()));
        juntarBarra(sb, t.paisResultado());
        return sb.length() == 0 ? "-" : esc(sb.toString());
    }

    // ---- tributacao federal (2.1.9, regras da v1.02) ----

    private static void tributacaoFederal(StringBuilder h, Danfse.TributacaoFederal t, Danfse.Identificacao id) {
        boolean retido = t != null && "1".equals(t.tipoRetencaoPisCofins());
        BigDecimal sociaisRetidas = t == null ? null
            : retido
                ? somaDecimal(t.valorRetidoCsll(), t.valorPis(), t.valorCofins())
                : t.valorRetidoCsll();
        h.append("<table class=\"grid\">");
        row(h,
            btitle("TRIBUTAÇÃO FEDERAL (EXCETO CBS)"),
            campo("IRRF", money(t == null ? null : t.valorRetidoIrrf())),
            campo("Contribuição Previdenciária - Retida", money(t == null ? null : t.valorRetidoContribuicaoPrevidenciaria())),
            campo("Contribuições Sociais - Retidas", money(sociaisRetidas)));
        // Nota 6: linha PIS/COFINS impressa para competencia ate o fim do ano-calendario de 2026.
        LocalDate competencia = id == null ? null : id.competencia();
        if (competencia == null || competencia.getYear() <= 2026) {
            row(h,
                campo("PIS - Débito Apuração Própria", money(t == null ? null : (retido ? BigDecimal.ZERO : t.valorPis()))),
                campo("COFINS - Débito Apuração Própria", money(t == null ? null : (retido ? BigDecimal.ZERO : t.valorCofins()))),
                campoSpan("Descrição Contrib. Sociais - Retidas", dash(resumo(t == null ? null : t.descricaoRetencaoPisCofins(), 35)), 2));
        }
        h.append("</table>");
    }

    // ---- IBS/CBS (2.1.10) — bloco do modelo oficial; "-" enquanto a NT 009 nao estiver em producao ----

    private static void ibsCbs(StringBuilder h, Danfse.IbsCbs t) {
        h.append("<table class=\"grid\">");
        row(h,
            btitle("TRIBUTAÇÃO IBS / CBS"),
            campo("CST / cClassTrib", t == null ? "-" : barra(t.cst(), t.classificacaoTributaria())),
            campoSpan("Indicador de Operação / Código IBGE Incidência / Município Incidência / Sigla UF",
                t == null ? "-" : barra(t.indicadorOperacao(), t.codigoLocalIncidencia(),
                    t.localIncidencia(), MunicipioResolver.uf(t.codigoLocalIncidencia())), 2));
        row(h,
            campo("Exclusões e Reduções da Base de Cálculo", money(t == null ? null : t.exclusoesReducoesBase())),
            campo("Base de Cálculo Após Exclusões e Reduções", money(t == null ? null : t.baseCalculo())),
            campo("Red. Alíquota IBS / Red. Alíquota CBS", t == null ? "-"
                : barra(percentOuNull(t.reducaoAliquotaUf()), percentOuNull(t.reducaoAliquotaMunicipio()),
                    percentOuNull(t.reducaoAliquotaCbs()))),
            campo("Alíquota - IBS UF / IBS Mun", t == null ? "-"
                : barra(percentOuNull(t.aliquotaIbsUf()), percentOuNull(t.aliquotaIbsMunicipio()))));
        row(h,
            campo("Alíq. Efetiva Municipal - IBS", percent(t == null ? null : t.aliquotaEfetivaMunicipio())),
            campo("Valor Apurado Municipal - IBS", money(t == null ? null : t.valorIbsMunicipio())),
            campo("Alíq. Efetiva Estadual - IBS", percent(t == null ? null : t.aliquotaEfetivaUf())),
            campo("Valor Apurado Estadual - IBS", money(t == null ? null : t.valorIbsUf())));
        row(h,
            campo("Valor Total Apurado - IBS", money(t == null ? null : t.valorTotalIbs())),
            campo("Alíquota - CBS", percent(t == null ? null : t.aliquotaCbs())),
            campo("Alíquota Efetiva - CBS", percent(t == null ? null : t.aliquotaEfetivaCbs())),
            campo("Valor Total Apurado - CBS", money(t == null ? null : t.valorTotalCbs())));
        h.append("</table>");
    }

    // ---- valor total (2.1.11) ----

    private static void valorTotal(StringBuilder h, Danfse.Valores v, Danfse.IbsCbs ibs) {
        BigDecimal totalIbsCbs = ibs == null ? null : somaDecimal(ibs.valorTotalIbs(), ibs.valorTotalCbs());
        BigDecimal liquidoComIbsCbs = ibs == null ? null : ibs.valorLiquidoComIbsCbs();
        h.append("<table class=\"grid\">");
        row(h,
            btitle("VALOR TOTAL DA NFS-E"),
            campoCaps("VALOR DA OPERAÇÃO / SERVIÇO", money(v == null ? null : v.valorServico())),
            campo("Desconto Incondicionado", money(v == null ? null : v.descontoIncondicionado())),
            campo("Desconto Condicionado", money(v == null ? null : v.descontoCondicionado())));
        row(h,
            campo("Total das Retenções (ISSQN / Federais)", money(v == null ? null : v.totalRetencoes())),
            campoCaps("VALOR LÍQUIDO DA NFS-e", money(v == null ? null : v.valorLiquido())),
            campo("Total do IBS/CBS", money(totalIbsCbs)),
            campoCapsSombreado("VALOR LÍQUIDO DA NFS-e + IBS/CBS", money(liquidoComIbsCbs)));
        h.append("</table>");
    }

    // ---- informacoes complementares (2.1.12) ----

    private static void informacoesComplementares(StringBuilder h, Danfse d) {
        h.append("<table class=\"grid\"><tr><td class=\"btitlefull\" colspan=\"4\">INFORMAÇÕES COMPLEMENTARES</td></tr>");
        h.append("<tr><td colspan=\"4\" class=\"infocompl\"><div class=\"val\">");
        String texto = d.informacoesComplementares();
        if (texto != null && !texto.isBlank()) {
            h.append(esc(resumo(texto, 1997))).append("<br/>");
        }
        h.append(esc(totaisAproximados(d.totaisTributos())));
        h.append("</div></td></tr></table>");
    }

    /** Linha obrigatoria (Nota 10): "Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: ...". */
    private static String totaisAproximados(Danfse.TotaisTributos t) {
        String prefixo = "Totais Aproximados dos Tributos cfe. Lei nº 12.741/2012: ";
        if (t == null || t.naoInformado()) {
            return prefixo + "Federais: - ; Estaduais: - ; Municipais: -";
        }
        if (t.percentualSimplesNacional() != null) {
            return prefixo + percentText(t.percentualSimplesNacional())
                + " (percentual aproximado conforme opção pelo Simples Nacional)";
        }
        return prefixo
            + "Federais: " + valorOuPercent(t.valorFederal(), t.percentualFederal())
            + " ; Estaduais: " + valorOuPercent(t.valorEstadual(), t.percentualEstadual())
            + " ; Municipais: " + valorOuPercent(t.valorMunicipal(), t.percentualMunicipal());
    }

    private static String valorOuPercent(BigDecimal valor, BigDecimal percentual) {
        if (valor != null) {
            return moneyText(valor);
        }
        if (percentual != null) {
            return percentText(percentual);
        }
        return "-";
    }

    // ---- celulas ----

    private static void row(StringBuilder h, String... cells) {
        h.append("<tr>");
        for (String c : cells) {
            h.append(c);
        }
        h.append("</tr>");
    }

    private static String btitle(String titulo) {
        return "<td class=\"btitle\">" + esc(titulo) + "</td>";
    }

    private static void bandline(StringBuilder h, String texto) {
        h.append("<table class=\"grid\"><tr><td colspan=\"4\" class=\"bandline\">")
            .append(esc(texto)).append("</td></tr></table>");
    }

    private static String campo(String label, String valueHtml) {
        return "<td><div class=\"lbl\">" + esc(label) + "</div><div class=\"val\">" + valueHtml + "</div></td>";
    }

    private static String campoCaps(String label, String valueHtml) {
        return "<td><div class=\"lblid\">" + esc(label) + "</div><div class=\"val\">" + valueHtml + "</div></td>";
    }

    private static String campoCapsSombreado(String label, String valueHtml) {
        return "<td class=\"shade\"><div class=\"lblid\">" + esc(label) + "</div><div class=\"val\">"
            + valueHtml + "</div></td>";
    }

    private static String campoSpan(String label, String valueHtml, int span) {
        return "<td colspan=\"" + span + "\"><div class=\"lbl\">" + esc(label)
            + "</div><div class=\"val\">" + valueHtml + "</div></td>";
    }

    private static String campoId(String label, String valueHtml) {
        return "<td><div class=\"lblid\">" + esc(label) + "</div><div class=\"val\">" + valueHtml + "</div></td>";
    }

    private static String campoIdSombreado(String label, String valueHtml) {
        return "<td class=\"shade\"><div class=\"lblid\">" + esc(label) + "</div><div class=\"val\">"
            + valueHtml + "</div></td>";
    }

    // ---- formatadores ----

    private static String documento(Danfse.Pessoa p) {
        if (p == null) {
            return "-";
        }
        if (p.cnpj() != null) {
            return mascaraCnpj(p.cnpj());
        }
        if (p.cpf() != null) {
            return mascaraCpf(p.cpf());
        }
        if (p.nif() != null) {
            return esc(p.nif());
        }
        return "-";
    }

    private static String endereco(Danfse.Pessoa p) {
        if (p == null || p.endereco() == null) {
            return "-";
        }
        Danfse.Endereco e = p.endereco();
        StringBuilder sb = new StringBuilder();
        juntar(sb, e.logradouro());
        juntar(sb, e.numero());
        juntar(sb, e.complemento());
        juntar(sb, e.bairro());
        return sb.length() == 0 ? "-" : esc(resumo(sb.toString(), 77));
    }

    private static String municipioPessoa(Danfse.Pessoa p) {
        if (p.endereco() == null) {
            return "-";
        }
        String mun = p.endereco().municipio();
        String uf = p.endereco().uf();
        if (uf == null || uf.isBlank()) {
            // A NT exige a sigla em todos os blocos, mas so o emitente costuma trazer a tag <UF>:
            // nos demais o endereco vem com o codigo IBGE, de onde a sigla e derivada sem rede.
            uf = MunicipioResolver.uf(p.endereco().codigoMunicipio());
        }
        if (mun == null && uf == null) {
            return "-";
        }
        // Se o municipio ainda e um codigo IBGE (nao veio nomeado no XML), tenta resolver no IBGE.
        // A resolucao ja inclui a UF ("Cidade - UF"); fallback gracioso para codigo + UF do endereco.
        if (mun != null && mun.matches("\\d{7}")) {
            var resolvido = MunicipioResolver.resolver(mun);
            if (resolvido.isPresent()) {
                return esc(resolvido.get());
            }
        }
        return esc((mun == null ? "" : mun) + (uf == null ? "" : " / " + uf));
    }

    private static String codigoIbgeCep(Danfse.Pessoa p) {
        if (p.endereco() == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        juntarBarra(sb, p.endereco().codigoMunicipio());
        juntarBarra(sb, cep(p.endereco().cep()));
        return sb.length() == 0 ? "-" : esc(sb.toString());
    }

    private static void juntar(StringBuilder sb, String parte) {
        if (parte != null && !parte.isBlank()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(parte);
        }
    }

    private static void juntarBarra(StringBuilder sb, String parte) {
        if (parte != null && !parte.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(parte);
        }
    }

    private static String barra(String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String parte : partes) {
            juntarBarra(sb, parte);
        }
        return sb.length() == 0 ? "-" : esc(sb.toString());
    }

    private static String primeiro(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static boolean temAlgum(String... valores) {
        return primeiro(valores) != null;
    }

    /** Trunca em limite de palavra e adiciona reticencias (regra da NT para campos excedidos). */
    private static String resumo(String texto, int max) {
        if (texto == null || texto.length() <= max) {
            return texto;
        }
        int corte = texto.lastIndexOf(' ', max);
        if (corte < max / 2) {
            corte = max;
        }
        return texto.substring(0, corte).trim() + "...";
    }

    private static String data(LocalDate d) {
        return d == null ? "-" : d.format(DATA);
    }

    private static String dataHora(OffsetDateTime d) {
        return d == null ? "-" : d.format(DATA_HORA);
    }

    private static String money(BigDecimal v) {
        return v == null ? "-" : moneyText(v);
    }

    private static String moneyText(BigDecimal v) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(BR);
        DecimalFormat f = new DecimalFormat("#,##0.00", s);
        return "R$ " + f.format(v.setScale(2, RoundingMode.HALF_UP));
    }

    private static String percent(BigDecimal v) {
        return v == null ? "-" : percentText(v);
    }

    private static String percentOuNull(BigDecimal v) {
        return v == null ? null : percentText(v);
    }

    private static String percentText(BigDecimal v) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(BR);
        DecimalFormat f = new DecimalFormat("#,##0.00", s);
        return f.format(v) + "%";
    }

    private static BigDecimal somaDecimal(BigDecimal... parcelas) {
        BigDecimal total = null;
        for (BigDecimal parcela : parcelas) {
            if (parcela != null) {
                total = total == null ? parcela : total.add(parcela);
            }
        }
        return total;
    }

    private static String telefone(String fone) {
        if (fone == null) {
            return "-";
        }
        String d = fone.replaceAll("\\D", "");
        if (d.length() == 11) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7);
        }
        if (d.length() == 10) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6);
        }
        return esc(fone);
    }

    private static String cep(String cep) {
        if (cep == null) {
            return null;
        }
        String d = cep.replaceAll("\\D", "");
        return d.length() == 8 ? d.substring(0, 5) + "-" + d.substring(5) : cep;
    }

    private static String mascaraCnpj(String v) {
        String d = v.replaceAll("\\D", "");
        if (d.length() != 14) {
            return esc(v);
        }
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8)
            + "/" + d.substring(8, 12) + "-" + d.substring(12);
    }

    private static String mascaraCpf(String v) {
        String d = v.replaceAll("\\D", "");
        if (d.length() != 11) {
            return esc(v);
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    private static String dash(String v) {
        return v == null || v.isBlank() ? "-" : esc(v);
    }

    private static String dashText(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private static String esc(String v) {
        if (v == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(v.length() + 16);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String css() {
        // Modelo do Anexo I da NT 008 v1.02: pagina A4 retrato com margens de 0,15-0,20cm,
        // borda externa de 1pt, linhas divisorias de 0,5pt, sombreamento cinza 5% (#f2f2f2) no
        // cabecalho/titulos de bloco/campos destacados, fontes minimas 6pt (labels) e 7pt
        // (conteudo). Fonte unica embutida: Liberation Sans (metrica da Arial, licenca OFL).
        return """
            @page { size: A4 portrait; margin: 2mm; }
            * { box-sizing: border-box; }
            html { height: 100%; }
            body { font-family: 'Liberation Sans', Arial, sans-serif; font-size: 7pt; color: #000;
                   background: #fff; margin: 0; height: 100%; }
            table { width: 100%; border-collapse: collapse; table-layout: fixed; }
            /* A moldura ocupa a pagina inteira: o item 2.2.2 limita a margem inferior a 0,20cm,
               e os itens 2.3.1 a 2.3.3 mandam devolver a altura liberada por blocos suprimidos
               ao quadro de Descricao do Servico e/ou Informacoes Complementares, que e o ultimo
               do documento e portanto absorve a sobra. */
            .frame { border: 1pt solid #000; height: 100%; }
            .hdr { background: #f2f2f2; }
            .hdr td { padding: 2pt 4pt; vertical-align: middle; }
            .hdr-l { width: 32%; text-align: left; }
            .hdr-c { width: 40%; text-align: center; }
            .hdr-r { width: 28%; text-align: left; }
            .logo { max-height: 24pt; max-width: 4cm; vertical-align: middle; }
            .logo-emit { max-height: 24pt; max-width: 30%; vertical-align: middle; margin-left: 4pt; }
            .brasao { max-height: 14pt; vertical-align: middle; margin-right: 3pt; }
            .tit { font-size: 9pt; font-weight: bold; }
            .aviso { color: #ff0000; font-size: 9pt; font-weight: bold; }
            .mun { font-size: 8pt; }
            .amb { font-size: 6pt; }
            .grid td { border-top: 0.5pt solid #000; padding: 1pt 4pt; vertical-align: top; }
            .lbl { font-size: 6pt; font-weight: bold; line-height: 1.1; }
            .lblid { font-size: 7pt; font-weight: bold; line-height: 1.1; }
            .val { font-size: 7pt; line-height: 1.15; }
            .btitle { font-size: 7pt; font-weight: bold; background: #f2f2f2; vertical-align: middle; }
            .btitlefull { font-size: 7pt; font-weight: bold; background: #f2f2f2; }
            .bandline { text-align: center; font-size: 7pt; font-weight: bold;
                        border-top: 0.5pt solid #000; padding: 1.5pt 4pt; }
            .shade { background: #f2f2f2; }
            .qrcell { width: 4.72cm; text-align: center; vertical-align: top; padding-top: 3pt; }
            /* 1,52cm e a medida do SIMBOLO (NT 008, item 2.4.3): o bitmap nao traz quiet zone.
               Ela volta como margem, senao o simbolo encosta na linha do cabecalho. O padrao QR
               pede 4 modulos livres; com 45 a 49 modulos em 1,52cm, 4pt cobrem essa folga. */
            .qr { width: 1.52cm; height: 1.52cm; margin: 4pt; }
            .qrtxt { font-size: 6pt; text-align: left; margin-top: 2pt; }
            .descserv { height: 3.0cm; }
            .infocompl { height: 5.2cm; }
            """;
    }
}

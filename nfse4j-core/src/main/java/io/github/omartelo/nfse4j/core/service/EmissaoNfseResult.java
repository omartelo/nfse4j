package io.github.omartelo.nfse4j.core.service;

import io.github.omartelo.nfse4j.core.http.NfseHttpResponse;

public record EmissaoNfseResult(NfseHttpResponse response, String xml, String signedXml) {
}

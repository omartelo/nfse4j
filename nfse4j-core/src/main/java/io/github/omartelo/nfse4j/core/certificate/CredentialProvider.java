package io.github.omartelo.nfse4j.core.certificate;

@FunctionalInterface
public interface CredentialProvider {
    char[] resolvePassword();
}

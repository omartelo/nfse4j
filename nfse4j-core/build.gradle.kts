plugins {
    `java-library`
}

description = "Motor Java (zero dependencias de runtime) para emissao de NFS-e Nacional."

dependencies {
    testImplementation(libs.bouncycastle.prov)
    testImplementation(libs.bouncycastle.pkix)
}

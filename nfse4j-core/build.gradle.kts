plugins {
    `java-library`
}

description = "Motor Java para emissao de NFS-e Nacional."

dependencies {
    implementation(libs.gson)
    testImplementation(libs.bouncycastle.prov)
    testImplementation(libs.bouncycastle.pkix)
}

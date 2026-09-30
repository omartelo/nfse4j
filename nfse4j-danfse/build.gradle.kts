plugins {
    `java-library`
}

description = "Gera o DANFSe (PDF) localmente a partir do XML autorizado da NFS-e Nacional."

dependencies {
    implementation(project(":nfse4j-core"))
    implementation(libs.openhtmltopdf.pdfbox)
    implementation(libs.zxing.core)
}

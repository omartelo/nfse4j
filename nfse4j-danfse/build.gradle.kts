plugins {
    `java-library`
}

description = "Gera o DANFSe (PDF) localmente a partir do XML autorizado da NFS-e Nacional."

dependencies {
    implementation(libs.pdfbox)
    implementation(libs.openhtmltopdf.pdfbox)
    implementation(libs.zxing.core)
}

tasks.test {
    // Regenera as referencias de src/test/resources/golden/: ./gradlew :nfse4j-danfse:test -Dgolden.update=true
    providers.systemProperty("golden.update").orNull?.let { systemProperty("golden.update", it) }
}

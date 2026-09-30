plugins {
    alias(libs.plugins.shadow)
}

description = "CLI de NFS-e Nacional para humanos e agentes que rodam shell."

dependencies {
    implementation(project(":nfse4j-core"))
    implementation(project(":nfse4j-danfse"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
}

tasks.shadowJar {
    archiveFileName = "nfse4j-cli.jar"
    manifest {
        attributes["Main-Class"] = "io.github.omartelo.nfse4j.cli.Cli"
    }
}

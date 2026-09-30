plugins {
    alias(libs.plugins.shadow)
}

description = "Servidor MCP (stdio) que expoe a emissao de NFS-e Nacional para agentes de IA."

dependencies {
    implementation(project(":nfse4j-core"))
    implementation(project(":nfse4j-danfse"))
    // O MCP SDK traz Jackson 3 (tools.jackson). Nao misturar com Jackson 2 aqui: as
    // jackson-annotations 2.x sombreiam as 3.x e quebram o parsing de schema. Para
    // converter/serializar usamos o proprio McpJsonMapper do SDK.
    implementation(libs.mcp)
}

tasks.shadowJar {
    from(rootProject.files("LICENSE", "THIRD-PARTY.md")) {
        into("META-INF/nfse4j")
    }
    archiveFileName = "nfse4j-mcp.jar"
    manifest {
        attributes["Main-Class"] = "io.github.omartelo.nfse4j.mcp.NfseMcpServer"
    }
    // Sem INCLUDE o Shadow descarta os META-INF/services duplicados antes do merge, e o MCP SDK
    // e o Jackson 3 registram implementacoes por ServiceLoader.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    filesNotMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.maven.publish) apply false
}

val catalog = libs

subprojects {
    apply(plugin = "java")

    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addBooleanOption("Xdoclint:none", true)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"(platform(catalog.junit.bom))
        "testImplementation"(catalog.junit.jupiter)
        "testRuntimeOnly"(catalog.junit.platform.launcher)
    }
}

// Publicacao no Maven Central (Sonatype Central Portal): feita pelo workflow de release ao enviar uma
// tag v*, com as credenciais nos secrets do repo. Manual: ./gradlew publishToMavenCentral com
// mavenCentralUsername/mavenCentralPassword e signingInMemoryKey/signingInMemoryKeyPassword em
// ~/.gradle/gradle.properties (ou ORG_GRADLE_PROJECT_*). cli e mcp sao apps (fat jars) e ficam de fora.
configure(listOf(project(":nfse4j-core"), project(":nfse4j-danfse"))) {
    apply(plugin = "com.vanniktech.maven.publish")

    configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()

        pom {
            name.set(project.name)
            description.set(provider { project.description })
            url.set("https://github.com/omartelo/nfse4j")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("rafael-matos-dev")
                    name.set("Rafael Matos")
                    url.set("https://github.com/rafael-matos-dev")
                }
                developer {
                    id.set("omartelo")
                    name.set("omartelo")
                    url.set("https://github.com/omartelo")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/omartelo/nfse4j.git")
                developerConnection.set("scm:git:git@github.com:omartelo/nfse4j.git")
                url.set("https://github.com/omartelo/nfse4j")
            }
        }
    }

    // Assinatura obrigatoria so rumo ao Central, para publishToMavenLocal funcionar sem chave GPG.
    configure<SigningExtension> {
        setRequired { gradle.taskGraph.allTasks.any { it.name.endsWith("ToMavenCentral") } }
    }
}

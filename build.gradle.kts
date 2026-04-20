import java.time.Year

plugins {
    java
    application
    jacoco
    id("com.diffplug.spotless") version "8.4.0"
    id("org.jreleaser") version "1.23.0"
    id("com.google.cloud.tools.jib") version "3.5.3"
    id("pl.allegro.tech.build.axion-release") version "1.21.1"
    id("org.cyclonedx.bom") version "3.2.3"
    id("com.github.jk1.dependency-license-report") version "3.1.2"
}

// Force JGit 6.x for JReleaser compatibility — JReleaser 1.23.0 uses
// GpgObjectSigner which was removed in JGit 7.x (jreleaser/jreleaser#1846).
// DO NOT accept Dependabot PRs that bump this to 7.x — it will break releases.
buildscript {
    configurations.classpath {
        resolutionStrategy {
            force("org.eclipse.jgit:org.eclipse.jgit:7.6.0.202603022253-r")
        }
    }
}

group = "com.guidedbyte"
version = scmVersion.version

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Force Jackson 2.21.2+ to fix CVE in jackson-core async parser (DoS via number length bypass)
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.21.2"))

    // MCP Server (transitively provides jackson-databind)
    implementation("io.modelcontextprotocol.sdk:mcp-core:1.1.1")
    implementation("io.modelcontextprotocol.sdk:mcp-json-jackson2:1.1.1")

    // Embedded database
    implementation("com.h2database:h2:2.4.240")

    // CLI
    implementation("info.picocli:picocli:4.7.7")
    annotationProcessor("info.picocli:picocli-codegen:4.7.7")

    // Logging (slf4j-api provided transitively by logback and MCP SDK)
    implementation("ch.qos.logback:logback-classic:1.5.32")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Fuzz testing
    testImplementation("com.code-intelligence:jazzer-junit:0.30.0")
}

application {
    mainClass.set("com.guidedbyte.sheriff.SheriffApplication")
}

// Generate version.properties at build time (configuration cache compatible)
tasks.register("generateVersionProperties") {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    val projectVersion = provider { project.version.toString() }
    outputs.dir(outputDir)
    inputs.property("version", projectVersion)

    doLast {
        val propsFile = outputDir.get().file("version.properties").asFile
        propsFile.parentFile.mkdirs()
        propsFile.writeText("version=${projectVersion.get()}\n")
    }
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/resources"))
        }
    }
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        classDirectories.files.map {
            fileTree(it) {
                // Exclude bootstrap/entry-point classes that can't be meaningfully unit tested
                exclude(
                    "com/guidedbyte/sheriff/SheriffApplication.class",
                    "com/guidedbyte/sheriff/cli/**",
                    "com/guidedbyte/sheriff/mcp/SheriffMcpServer.class",
                )
            }
        },
    )
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.guidedbyte.sheriff.SheriffApplication"
    }
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    // EXCLUDE is intentional: this project does not use ServiceLoader or Jackson module
    // auto-discovery. If service file merging becomes needed, switch to the Shadow plugin.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.guidedbyte.sheriff.SheriffApplication"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath
            .get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

// Compute copyright year: "2026" initially, "2026-YYYY" when year changes
val inceptionYear = 2026
val currentYear = Year.now().value
val copyrightYear = if (currentYear > inceptionYear) "$inceptionYear-$currentYear" else "$inceptionYear"

spotless {
    java {
        licenseHeader(
            """
            /*
             * Copyright $copyrightYear GuidedByte Technologies Inc.
             *
             * Licensed under the Apache License, Version 2.0 (the "License");
             * you may not use this file except in compliance with the License.
             * You may obtain a copy of the License at
             *
             *     http://www.apache.org/licenses/LICENSE-2.0
             *
             * Unless required by applicable law or agreed to in writing, software
             * distributed under the License is distributed on an "AS IS" BASIS,
             * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
             * See the License for the specific language governing permissions and
             * limitations under the License.
             */
            """.trimIndent(),
        )
        palantirJavaFormat("2.50.0")
        importOrder("java|javax", "com.guidedbyte", "", "\\#")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        target("src/**/*.java")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.5.0")
    }
}

// Note: CI runs spotlessCheck explicitly. Developers should run spotlessApply manually
// or via a pre-commit hook. Auto-formatting on compile is intentionally disabled to
// avoid masking formatting violations in CI.

// Jib container image configuration
jib {
    from {
        image = "eclipse-temurin:21-jre-noble"
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }
    to {
        image = "ghcr.io/ryansmith4/sheriff-mcp"
        auth {
            username = System.getenv("JIB_USERNAME") ?: ""
            password = System.getenv("JIB_PASSWORD") ?: ""
        }
    }
    container {
        mainClass = "com.guidedbyte.sheriff.SheriffApplication"
        args = listOf("start")
        ports = emptyList()
        workingDirectory = "/data"
        user = "1001:1001"
        labels.putAll(
            mapOf(
                "org.opencontainers.image.title" to "Sheriff-MCP",
                "org.opencontainers.image.description" to "AI-powered static analysis issue fixer - MCP server for SARIF reports",
                "org.opencontainers.image.url" to "https://github.com/ryansmith4/sheriff-mcp",
                "org.opencontainers.image.source" to "https://github.com/ryansmith4/sheriff-mcp",
                "org.opencontainers.image.licenses" to "Apache-2.0",
                "org.opencontainers.image.version" to version.toString(),
                "io.modelcontextprotocol.server.name" to "io.github.ryansmith4/sheriff-mcp",
            ),
        )
        volumes = listOf("/data")
    }
}

// Axion release plugin configuration
scmVersion {
    tag {
        prefix.set("v")
        initialVersion.set({ _, _ -> "1.0.0" })
    }
}

// Dependency license report
licenseReport {
    renderers =
        arrayOf(
            com.github.jk1.license.render
                .TextReportRenderer(),
            com.github.jk1.license.render
                .JsonReportRenderer(),
        )
    filters =
        arrayOf(
            com.github.jk1.license.filter
                .LicenseBundleNormalizer(),
        )
    allowedLicensesFile = file("config/allowed-licenses.json")
}

// CycloneDX SBOM generation
tasks.named("cyclonedxDirectBom") {
    mustRunAfter("fatJar")
}

// JReleaser configuration
jreleaser {
    project {
        name.set("sheriff-mcp")
        description.set("AI-powered static analysis issue fixer - MCP server for SARIF reports")
        authors.add("Ryan Smith")
        license.set("Apache-2.0")
        links {
            homepage.set("https://github.com/ryansmith4/sheriff-mcp")
        }
        inceptionYear.set("2026")
    }

    // Signing is handled by GitHub Actions attestations (actions/attest-build-provenance)
    // and cosign in the docker job, not by JReleaser's built-in signing.
    signing {
        active.set(org.jreleaser.model.Active.NEVER)
    }

    release {
        github {
            repoOwner.set("ryansmith4")
            name.set("sheriff-mcp")
            overwrite.set(true)
            tagName.set("v{{projectVersion}}")
            changelog {
                formatted.set(org.jreleaser.model.Active.ALWAYS)
                preset.set("conventional-commits")
                contributors {
                    enabled.set(true)
                }
                hide {
                    contributors.add("GitHub")
                    contributors.add("dependabot")
                    contributors.add("[bot]")
                }
            }
        }
    }

    distributions {
        create("sheriff") {
            distributionType.set(org.jreleaser.model.Distribution.DistributionType.SINGLE_JAR)
            artifact {
                path.set(file("build/libs/sheriff-mcp-$version-all.jar"))
            }
        }
    }

    files {
        artifact {
            path.set(file("build/reports/cyclonedx-direct/bom.json"))
            extraProperties.put("skipSigning", true)
        }
    }
}

// Ensure fatJar is built before JReleaser runs
tasks.named("jreleaserFullRelease") {
    dependsOn("fatJar")
}

tasks.named("jreleaserPackage") {
    dependsOn("fatJar")
}

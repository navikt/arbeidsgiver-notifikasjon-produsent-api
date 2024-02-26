plugins {
    kotlin("jvm") version "1.9.22"
    id("org.cyclonedx.bom") version "1.7.4"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug") {
        exclude(group = "net.bytebuddy", module = "byte-buddy-agent")
        exclude(group = "net.bytebuddy", module = "byte-buddy")
    }

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")

    val ktorVersion = "2.3.7"
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-apache-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")

    implementation("no.nav.arbeidsgiver:altinn-rettigheter-proxy-klient:3.1.0")
    implementation("no.nav.tjenestespesifikasjoner:altinn-notification-agency-external-basic:1.2019.09.25-00.21-49b69f0625e0")

    val cxfVersion = "3.5.7"
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")

    implementation("com.graphql-java:graphql-java:19.4")
    implementation("io.micrometer:micrometer-registry-prometheus:1.10.5")
    implementation("com.symbaloo:graphql-micrometer:1.0.1")
    implementation("net.logstash.logback:logstash-logback-encoder:7.3")
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.apache.kafka:kafka-clients:3.6.0")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("org.flywaydb:flyway-core:9.16.0")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.google.cloud.sql:postgres-socket-factory:1.13.0")
    implementation("com.google.guava:guava") {
        version {
            strictly("32.1.3-jre")
        }
    }
    implementation("com.nimbusds:nimbus-jose-jwt:9.30.2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.5")
    implementation("org.xerial:sqlite-jdbc:3.43.2.1")

    testImplementation("org.apache.cxf:cxf-rt-transports-http-jetty:$cxfVersion")

    val kotestVersion = "5.7.2"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-framework-datatest:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-assertions-ktor:2.0.0")
    testImplementation("javax.xml.bind:jaxb-api:2.3.1")
    testImplementation("com.sun.xml.ws:jaxws-ri:2.3.3")
    testImplementation("io.kotest.extensions:kotest-extensions-mockserver:1.3.0") {
        exclude(group = "commons-collections", module = "commons-collections")
        exclude(group = "org.yaml", module = "snakeyaml")
    }

    testImplementation("io.mockk:mockk-jvm:1.13.8")
    testImplementation("com.jayway.jsonpath:json-path:2.9.0")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
}

group = "no.nav.arbeidsgiver.notifikasjon"
version = "1.0-SNAPSHOT"

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

//tasks.withType<JavaCompile>() {
//    options.encoding = "UTF-8"
//}

val mainClass = "no.nav.arbeidsgiver.notifikasjon.MainKt"

tasks.jar {
    destinationDirectory.set(file("$buildDir/"))
    manifest {
        attributes["Main-Class"] = mainClass
        attributes["Class-Path"] = configurations.runtimeClasspath.get()
            .joinToString(separator = " ") { "lib/${it.name}" }
    }
}

tasks.assemble {
    configurations.runtimeClasspath.get().forEach {
        val file = File("$buildDir/lib/${it.name}")
        if (!file.exists())
            it.copyTo(file)
    }
}

tasks.build {
    dependsOn("cyclonedxBom")
}

tasks.cyclonedxBom {
    setIncludeConfigs(listOf("runtimeClasspath"))
    setSkipConfigs(listOf("compileClasspath", "testCompileClasspath"))
    setSkipProjects(listOf(rootProject.name))
    setOutputFormat("json")
}
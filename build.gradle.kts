import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.testing.jacoco.tasks.JacocoReportBase

plugins {
    id("java")
    id("eclipse")
    id("distribution")
    id("jacoco")
    id("checkstyle")
    id("com.github.spotbugs") version "6.0.26"
}

layout.buildDirectory.set(layout.projectDirectory.dir("build/idiomatic-scratch"))
reporting.baseDirectory.set(layout.projectDirectory.dir("build/quality-reports"))

// Define where IDE IntelliSense helpers are stored.
eclipse {
    classpath {
        baseSourceOutputDir.set(layout.buildDirectory.dir("ide"))
        defaultOutputDir = layout.buildDirectory.dir("ide/default").get().asFile
    }
}

base {
    archivesName.set("software-materialization-service")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

jacoco {
    toolVersion = "0.8.14"
}

checkstyle {
    toolVersion = "10.17.0"
    configFile = file("checkstyle-configuration.xml")
    isIgnoreFailures = false
}

spotbugs {
    toolVersion.set("4.9.8")
    ignoreFailures.set(false)
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.LOW)
}

repositories {
    mavenCentral()
    maven("https://repo.jenkins-ci.org/releases/") {
        content {
            includeModule("com.infradna.tool", "bridge-method-annotation")
            includeModule("org.jenkins-ci", "annotation-indexer")
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

dependencies {
    constraints {
        // Guice pulls in an older Guava that uses sun.misc.Unsafe (terminally deprecated in JDK 25). Force a newer version that migrated to VarHandle, eliminating the warning.
        implementation("com.google.guava:guava:33.4.8-jre")
    }

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // Dependency Injection
    implementation("com.google.inject:guice:7.0.0")

    // JSON and YAML `serde`
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")

    // GitHub Interaction
    implementation("org.kohsuke:github-api:1.330")
    compileOnly("com.infradna.tool:bridge-method-annotation:1.30")
    testCompileOnly("com.infradna.tool:bridge-method-annotation:1.30")

    // Validators
    implementation(platform("org.hibernate.validator:hibernate-validator-bom:9.1.0.Final"))
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("org.glassfish.expressly:expressly:6.0.0")

    // Spotbugs
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.9.8")

    // JUnit
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mockito
    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.21.0")
}

/**
 * Include the application JAR and runtime dependency JARs in the standard build.
 */

distributions {
    main {
        contents {
            from(tasks.named("jar"))
            from(configurations.runtimeClasspath)
        }
    }
}

tasks.named("distZip") { enabled = false }
tasks.named("distTar") { enabled = false }

tasks.named<Sync>("installDist") {
    into(layout.projectDirectory.dir("build/release/idiomatic-artifacts"))
}

tasks.named("build") {
    dependsOn(tasks.named("installDist"))
}

/**
 * Gradle Compile Task Configurations
 */

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Enable all javac lint warnings (unused variables, fallthrough, etc.) and treat them as errors
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

/**
 * Gradle Test Task Configurations
 */

tasks.test {
    // Jacoco appends to the bootstrap classpath for instrumentation, which prevents CDS (Class Data
    // Sharing) from working and produces a noisy warning. Disable CDS here since it is a startup
    // optimization for long-lived processes and irrelevant for a short-lived test JVM.
    jvmArgs("-Xshare:off")
    // Guice uses an unsafe JDK operation, this flag removes the warning
    systemProperty("guice_custom_class_loading", "CHILD")
    useJUnitPlatform()
    reports { junitXml.required = true; html.required = true }
    testLogging {
        events("skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

/**
 * Gradle Check Task Configurations
 */

tasks.check {
    dependsOn(
        tasks.jacocoTestCoverageVerification,
        tasks.jacocoTestReport,
        tasks.checkstyleMain,
        tasks.checkstyleTest,
        tasks.spotbugsMain,
        tasks.spotbugsTest
    )
}

/**
 * Jacaco Configurations
 */

tasks.withType<JacocoReportBase>().configureEach {
    val excludes = listOf(
        "io/materializer/Main.class",
        "io/materializer/composition/*"
    )
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(excludes)
        }
    )
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)

    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

/**
 * Checkstyle Configurations
 */

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}

/**
 * Spotbugs Configurations
 */

tasks.withType<SpotBugsTask>().configureEach {
    reports.maybeCreate("xml").required.set(true)
    reports.maybeCreate("html").required.set(true)
}

tasks.spotbugsMain {
    dependsOn(tasks.classes)
}

tasks.spotbugsTest {
    dependsOn(tasks.testClasses)
}

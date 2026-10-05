plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
    id("application")
    id("io.sentry.jvm.gradle") version "6.18.0"
    id("com.diffplug.spotless") version "8.9.0"
}

group = "es.redactado"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }

/**
 * Mockito's inline mock maker loads Byte Buddy's agent through the JDK's
 * self-attach mechanism, which is deprecated from JDK 21 onward and prints a
 * warning on every test JVM start. Resolving the agent separately lets the test
 * task load it at launch instead. Kept in its own configuration so it never
 * reaches the runtime classpath.
 */
val mockitoAgent: Configuration by configurations.creating

dependencies {
    implementation("net.dv8tion:JDA:6.5.0") { exclude(module = "opus-java") }
    implementation("com.google.inject:guice:7.0.0") // Dependency Injection
    implementation("de.exlll:configlib-yaml:4.8.1")
    implementation("com.github.ben-manes.caffeine:caffeine:v3.2.2")
    implementation("com.github.ben-manes.caffeine:jcache:v3.2.2")

    // Preset files are JSON, parsed with the version JDA already resolves
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")

    implementation("ch.qos.logback:logback-classic:1.6.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.fusesource.jansi:jansi:2.4.2")

    // Hibernate ORM
    implementation(platform("org.hibernate.orm:hibernate-platform:7.4.5.Final"))
    implementation("org.hibernate.orm:hibernate-hikaricp")
    implementation("org.hibernate.orm:hibernate-core")
    implementation("org.hibernate.orm:hibernate-jcache")
    implementation("org.hibernate.orm:hibernate-community-dialects") // Used for SQLite dialect
    implementation("jakarta.transaction:jakarta.transaction-api")

    // Database Drivers
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.7")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.h2database:h2:2.4.240")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.assertj:assertj-core:3.26.3")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    mockitoAgent("net.bytebuddy:byte-buddy-agent:1.17.6")
}

tasks.test {
    // Filesystem tests drive a real WatchService and can be slow on some file systems.
    // Skip them with: ./gradlew test -PexcludeTags=filesystem
    val excludedTags: String? = providers.gradleProperty("excludeTags").orNull
    // Concurrency stress tests use the real pools and measure wall-clock percentiles, so they
    // are excluded by default: a slow machine should not fail a build for being slow. Run them
    // with: ./gradlew test -PrunStress
    val runStress = providers.gradleProperty("runStress").isPresent
    useJUnitPlatform {
        if (!runStress) {
            excludeTags("stress")
        }
        if (excludedTags != null) {
            excludedTags.split(",").map(String::trim).filter(String::isNotEmpty).forEach { tag ->
                excludeTags(tag)
            }
        }
    }

    jvmArgs("-javaagent:${mockitoAgent.asPath}")

    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }
}

application {
    mainClass.set("es.redactado.Main")
}

spotless {
    //ratchetFrom("origin/main") // Uncomment in case of working in a feature branch (makes changes only to files on that branch)

    format("misc") {
        target(
            "*.gradle.kts",
            ".gitattributes",
            ".gitignore"
        )

        trimTrailingWhitespace()
        endWithNewline()
    }

    java {
        googleJavaFormat("1.26.0").aosp().reflowLongStrings().skipJavadocFormatting()
        formatAnnotations()
        removeUnusedImports()
    }
}

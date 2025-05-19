plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta13"
    id("application")
    id("io.sentry.jvm.gradle") version "5.6.0"
    id("com.diffplug.spotless") version "7.0.3"
}

group = "es.redactado"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.freya022:JDA:4a9d724a21") { exclude(module = "opus-java") }
    implementation("club.minnced:discord-webhooks:0.8.4") // Discord Webhooks
    implementation("com.google.inject:guice:7.0.0") // Dependency Injection
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("com.github.ben-manes.caffeine:jcache:3.2.0")
    implementation("io.github.cdimascio:dotenv-java:3.2.0")

    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.fusesource.jansi:jansi:2.4.2")

    implementation("com.github.nguyenq:tess4j:tess4j-5.15.0") // Tessarect OCR

    // Hibernate ORM
    implementation(platform("org.hibernate.orm:hibernate-platform:7.0.0.CR1"))
    implementation("org.hibernate.orm:hibernate-hikaricp")
    implementation("org.hibernate.orm:hibernate-core")
    implementation("org.hibernate.orm:hibernate-jcache")
    implementation("org.hibernate.orm:hibernate-community-dialects") // Used for SQLite dialect
    implementation("jakarta.transaction:jakarta.transaction-api")

    // Database Drivers
    implementation("com.zaxxer:HikariCP:3.6.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("com.h2database:h2:2.3.232")
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

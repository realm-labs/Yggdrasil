import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val appVersion = providers.gradleProperty("APP_VERSION").orElse("1.0.0")

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    runtimeOnly(libs.logback.classic)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "io.github.realmlabs.yggdrasil.MainKt"

        nativeDistributions {
            modules(
                "java.management",
                "java.naming",
                "java.security.jgss",
                "java.security.sasl",
                "jdk.unsupported",
            )
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Yggdrasil"
            packageVersion = appVersion.get()
            description = "A desktop ZooKeeper client"
            vendor = "Realm Labs"
            copyright = "Copyright (c) Realm Labs"

            linux {
                packageName = "yggdrasil"
                debMaintainer = "realm-labs@users.noreply.github.com"
            }
        }
    }
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly("com.android.tools.build:gradle:8.12.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
}

gradlePlugin {
    plugins {
        register("finbox-extension") {
            id = "finbox.plugins.extension"
            implementationClass = "dev.achmad.finbox.gradle.FinboxExtensionPlugin"
        }
    }
}

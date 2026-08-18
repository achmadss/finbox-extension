plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly("com.android.tools.build:gradle:9.3.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.9")
}

gradlePlugin {
    plugins {
        register("finbox-parser") {
            id = "finbox.plugins.parser"
            implementationClass = "dev.achmad.finbox.gradle.FinboxParserPlugin"
        }
    }
}

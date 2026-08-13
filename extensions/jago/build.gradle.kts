plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank Jago"
    provider = "jago"
    versionCode = 1
}

dependencies {
    implementation(project(":lib:receipt"))
}

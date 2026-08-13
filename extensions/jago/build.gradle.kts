plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank Jago"
    provider = "jago"
    versionCode = 4
}

dependencies {
    implementation(project(":lib:receipt"))
}

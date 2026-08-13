plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank Jago"
    provider = "jago"
    versionCode = 2
}

dependencies {
    implementation(project(":lib:receipt"))
}

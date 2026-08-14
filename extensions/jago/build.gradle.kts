plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank Jago"
    provider = "jago"
    versionCode = 5
}

dependencies {
    implementation(project(":lib:receipt"))
}

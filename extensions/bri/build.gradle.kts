plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank BRI"
    provider = "bri"
    versionCode = 8
}

dependencies {
    implementation(project(":lib:receipt"))
}

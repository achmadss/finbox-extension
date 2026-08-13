plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank BRI"
    provider = "bri"
    versionCode = 7
}

dependencies {
    implementation(project(":lib:receipt"))
}

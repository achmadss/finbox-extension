plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank BRI"
    provider = "bri"
    versionCode = 6
}

dependencies {
    implementation(project(":lib:receipt"))
}

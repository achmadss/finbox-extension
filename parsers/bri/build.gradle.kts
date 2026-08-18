plugins {
    id("finbox.plugins.parser")
}

finbox {
    name = "Bank BRI"
    provider = "bri"
    versionCode = 8
}

dependencies {
    implementation(project(":lib:receipt"))
}

plugins {
    id("finbox.plugins.parser")
}

finbox {
    name = "Bank BNI"
    provider = "bni"
    versionCode = 2
}

dependencies {
    implementation(project(":lib:receipt"))
}

plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank Mandiri"
    provider = "mandiri"
    versionCode = 1
}

dependencies {
    implementation(project(":lib:receipt"))
}

plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank Mandiri"
    provider = "mandiri"
    versionCode = 2
}

dependencies {
    implementation(project(":lib:receipt"))
}

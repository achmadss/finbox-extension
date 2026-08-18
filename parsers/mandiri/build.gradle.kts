plugins {
    id("finbox.plugins.parser")
}

finbox {
    name = "Bank Mandiri"
    provider = "mandiri"
    versionCode = 2
}

dependencies {
    implementation(project(":lib:receipt"))
}

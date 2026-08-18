plugins {
    id("finbox.plugins.parser")
}

finbox {
    name = "Bank Jago"
    provider = "jago"
    versionCode = 6
}

dependencies {
    implementation(project(":lib:receipt"))
}

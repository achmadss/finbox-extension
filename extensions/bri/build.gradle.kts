plugins {
    id("finbox.plugins.extension")
}

finbox {
    name = "Bank BRI"
    provider = "bri"
    versionCode = 1
    className = "dev.achmad.finbox.extension.bri.BriParser"
}

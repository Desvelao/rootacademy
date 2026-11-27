plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("app.root_academy.packcheck.MainKt")
}

// Run from the repository root so the default "content" path resolves.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    args(rootProject.file("content").path)
}

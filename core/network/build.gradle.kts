plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

dependencies {
    api(project(":core:model"))
    api(platform(libs.supabase.bom))
    api(libs.supabase.postgrest)
    api(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
}

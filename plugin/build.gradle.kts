import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val pluginName = "HealthConnectPlugin"

val pluginPackageName = "org.godotengine.plugin.android.healthconnectplugin"

android {
    namespace = pluginPackageName
    compileSdk = 33

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 26

        manifestPlaceholders["godotPluginName"] = pluginName
        manifestPlaceholders["godotPluginPackageName"] = pluginPackageName
        buildConfigField("String", "GODOT_PLUGIN_NAME", "\"${pluginName}\"")
        setProperty("archivesBaseName", pluginName)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.godotengine:godot:4.3.0.stable")
    // TODO: Additional dependencies should be added to export_plugin.gd as well.
    implementation("androidx.health.connect:connect-client:1.2.0-alpha01")
    implementation("androidx.compose.runtime:runtime:1.9.2")

}

// BUILD TASKS DEFINITION
// BUILD TASKS DEFINITION
val copyDebugAARToBin by tasks.registering(Copy::class) {
    description = "Copies the generated debug AAR binary to the plugin's bin directory"
    from("build/outputs/aar")
    include("$pluginName-debug.aar")
    into("A:/projects/apps/podo/addons/healthbridge/bin/debug")
}

val copyReleaseAARToBin by tasks.registering(Copy::class) {
    description = "Copies the generated release AAR binary to the plugin's bin directory"
    from("build/outputs/aar")
    include("$pluginName-release.aar")
    into("A:/projects/apps/podo/addons/healthbridge/bin/release")
}

val cleanBin by tasks.registering(Delete::class) {
    delete("A:/projects/apps/podo/addons/healthbridge/bin")
}

val copyAddonsToBin by tasks.registering(Copy::class) {
    description = "Copies the export scripts templates to the plugin's bin directory"

    dependsOn(cleanBin)
    finalizedBy(copyDebugAARToBin)
    finalizedBy(copyReleaseAARToBin)

//    from("export_scripts_template")
//    into("A:/projects/apps/podo/addons/healthbridge")
}


tasks.named("assemble").configure {
    finalizedBy(copyAddonsToBin)
}

tasks.named<Delete>("clean").apply {
    dependsOn(cleanBin)
}

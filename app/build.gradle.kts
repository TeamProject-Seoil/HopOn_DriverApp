plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.driver_bus_info"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.driver_bus_info"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // ✅ 추가된 부분 (Lottie Animation)
    implementation("com.airbnb.android:lottie:6.3.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.naver.maps:map-sdk:3.21.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation ("com.squareup.retrofit2:retrofit:2.11.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
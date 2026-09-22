plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace="com.anfas.samouchitel"; compileSdk=35
 defaultConfig { applicationId="com.anfas.samouchitel"; minSdk=26; targetSdk=35; versionCode=2; versionName="1.1" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies { implementation("androidx.appcompat:appcompat:1.7.0"); implementation("androidx.media3:media3-exoplayer:1.5.1"); implementation("androidx.media3:media3-session:1.5.1") }

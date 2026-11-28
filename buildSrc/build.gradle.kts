
repositories {
    // 优先使用阿里云镜像
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/central")
    // 兜底官方源
    mavenCentral()
}
dependencies {
    implementation(libs.gson)
    implementation(libs.jna)
    implementation(libs.kala.compress.tar)
    implementation(libs.kala.compress.ar)
    implementation(libs.weburl)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    into("org/jackhuang/hmcl/gradle/l10n") {
        from(projectDir.resolve("../JMCLCore/src/main/resources/assets/lang/"))
    }
}

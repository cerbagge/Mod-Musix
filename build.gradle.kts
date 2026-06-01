plugins {
    id("fabric-loom") version "1.13.6"
    id("com.gradleup.shadow") version "8.3.5"
}

version = property("mod_version") as String
group = property("maven_group") as String
base { archivesName.set("${property("archives_base_name")}-mc${stonecutter.current.version}") }

val javaVersion = if (stonecutter.eval(stonecutter.current.version, ">=1.20.5")) 21 else 17

repositories {
    mavenCentral()
}

// shade 전용 컨피규레이션 — fabric-loom 의 분류 시스템과 충돌 회피
val shadowConfig by configurations.creating

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // H2 — shade 컨피규레이션으로 우리 jar 내부에 relocate 하여 포함
    // Lunar/Ichor ClassLoader 격리 회피 (jar-in-jar 대신 직접 임베딩)
    shadowConfig(implementation("com.h2database:h2:2.2.224")!!)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("dev-shadow")
    configurations = listOf(shadowConfig)

    // org.h2 클래스를 우리 jar 내부 패키지로 relocate
    // 우리 코드는 org.h2.jdbcx.JdbcDataSource 그대로 사용하지만 bytecode 수준에서 자동 치환
    relocate("org.h2", "com.musix.shaded.h2")

    // 라이선스/메타데이터 정리
    exclude("META-INF/maven/**")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

// fabric-loom remapJar 가 shadowJar 결과를 입력으로 사용하도록
tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    dependsOn("shadowJar")
    val shadowJarTask = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar")
    inputFile.set(shadowJarTask.flatMap { it.archiveFile })
}

tasks.processResources {
    val mcVersion = stonecutter.current.version
    val javaVer = if (stonecutter.eval(mcVersion, ">=1.20.5")) 21 else 17

    val mcDep = when {
        stonecutter.eval(mcVersion, "=1.20.4") -> ">=1.20.2 <=1.20.4"
        stonecutter.eval(mcVersion, "=1.20.6") -> ">=1.20.5 <=1.20.6"
        stonecutter.eval(mcVersion, "=1.21.1") -> ">=1.21"
        else -> ">=1.20.2"
    }

    val mixinCompat = if (javaVer >= 21) "JAVA_21" else "JAVA_17"
    val javaDep = if (javaVer >= 21) ">=21" else ">=17"

    val props = mapOf(
        "version" to project.version,
        "mc_dep" to mcDep,
        "java_dep" to javaDep,
        "mixin_compat" to mixinCompat
    )
    inputs.properties(props)

    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("musix.mixins.json") { expand(props) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
}

java {
    withSourcesJar()
    val jv = JavaVersion.toVersion(javaVersion)
    sourceCompatibility = jv
    targetCompatibility = jv
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

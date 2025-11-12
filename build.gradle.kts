plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.spinyowl"
version = "1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

val pebbleVersion = "3.2.4"
val commonsCsvVersion = "1.14.1"
val snakeYmlVersion = "2.5"
val directoryMatcherVersion = "0.19.1"
val javafxVersion = "21"
val junitVersion = "6.0.1"

val slf4jVersion = "2.0.17"
val logbackVersion = "1.5.21"
val lombokVersion = "1.18.42"

dependencies {
    // --- Template / Parsing ---
    implementation("io.pebbletemplates:pebble:$pebbleVersion")
    implementation("org.apache.commons:commons-csv:$commonsCsvVersion")
    implementation("org.yaml:snakeyaml:$snakeYmlVersion")
    implementation("io.methvin:directory-watcher:$directoryMatcherVersion")
    // --- UI / Rendering ---
    implementation("org.openjfx:javafx-controls:$javafxVersion")
    implementation("org.openjfx:javafx-fxml:$javafxVersion")
    implementation("org.openjfx:javafx-web:$javafxVersion")
    // --- Logging ---
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    // --- Lombok (compile-time only) ---
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "21"
    modules("javafx.controls", "javafx.fxml", "javafx.web")
}

application {
    mainClass = "com.spinyowl.cards.MainApp"
}
tasks.test {
    useJUnitPlatform()
}
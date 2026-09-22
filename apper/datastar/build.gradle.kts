plugins {
  kotlin("jvm") version "2.4.20"
  kotlin("plugin.serialization") version "2.4.20"
  application
}

repositories { mavenCentral() }

dependencies {
  implementation("io.ktor:ktor-server-core:3.6.0")
  implementation("io.ktor:ktor-server-cio:3.6.0")
  implementation("io.ktor:ktor-server-sse:3.6.0")
  implementation("io.ktor:ktor-server-content-negotiation:3.6.0")
  implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
  implementation("io.ktor:ktor-client-core:3.6.0")
  implementation("io.ktor:ktor-client-cio:3.6.0")
  implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
  implementation("ch.qos.logback:logback-classic:1.5.20")

  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

kotlin { jvmToolchain(21) }

application { mainClass.set("no.fristil.forstelinja.datastar.MainKt") }

tasks.test {
  useJUnitPlatform()

  // Ingen `inputs.files(fileTree("../../felles"))` her: denne modulen leser
  // ikke de delte filene. Spilltjeneren gjør det, og har erklæringen.
}

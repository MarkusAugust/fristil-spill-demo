plugins {
  kotlin("jvm") version "2.4.20"
  kotlin("plugin.serialization") version "2.4.20"
  application
}

repositories { mavenCentral() }

dependencies {
  implementation("io.ktor:ktor-server-core:3.6.0")
  implementation("io.ktor:ktor-server-cio:3.6.0")
  // Datastar-protokollen: strømmen ned til nettleseren og signalene opp.
  implementation("io.github.markusaugust.streamlord:streamlord-ktor:0.1.0-rc1")
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

  // `felles/` er delte data, og Gradle kjenner dem ikke som en inngang.
  // Uten dette kan du endre `brett.css` eller en kommune, kjøre testene, og
  // få grønt på gammelt grunnlag fordi oppgaven regnes som oppdatert.
  inputs.files(fileTree("../../felles")).withPropertyName("fellesdata")
}

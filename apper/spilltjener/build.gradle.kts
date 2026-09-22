plugins {
  kotlin("jvm") version "2.4.20"
  kotlin("plugin.serialization") version "2.4.20"
  application
}

repositories { mavenCentral() }

dependencies {
  implementation("io.ktor:ktor-server-core:3.6.0")
  implementation("io.ktor:ktor-server-cio:3.6.0")
  implementation("io.ktor:ktor-server-content-negotiation:3.6.0")
  implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
  implementation("io.ktor:ktor-server-sse:3.6.0")
  implementation("io.ktor:ktor-server-cors:3.6.0")
  implementation("io.ktor:ktor-server-call-logging:3.6.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
  implementation("org.xerial:sqlite-jdbc:3.53.4.0")
  implementation("ch.qos.logback:logback-classic:1.5.20")

  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
  testImplementation("io.ktor:ktor-server-test-host:3.6.0")
}

kotlin { jvmToolchain(21) }

application { mainClass.set("no.fristil.forstelinja.MainKt") }

tasks.test {
  useJUnitPlatform()

  // `felles/` er delte data testene leser, men Gradle kjenner dem ikke som
  // en inngang. Uten dette kan du endre en sak eller en kommune, kjøre
  // testene, og få grønt på gammelt grunnlag fordi oppgaven regnes som
  // oppdatert.
  inputs.files(fileTree("../../felles")).withPropertyName("fellesdata")
}

@file:Suppress("UnstableApiUsage")

import org.checkerframework.gradle.plugin.CheckerFrameworkExtension
import org.gradle.kotlin.dsl.configure

plugins {
  `java-library`
  `jvm-test-suite`
  `maven-publish`
  signing
  id("eu.aylett.conventions") version "0.5.2"
  id("eu.aylett.plugins.version") version "0.5.2"
  id("org.checkerframework") version "0.6.53"
  id("com.diffplug.spotless") version "7.0.3"
  checkstyle
  id("info.solidsoft.pitest") version "1.15.0"
  id("com.groupcdg.pitest.github") version "1.0.7"
  id("com.github.spotbugs") version "6.1.10"
}

group = "eu.aylett"

version = aylett.versions.gitVersion()

repositories {
  // Use Maven Central for resolving dependencies.
  mavenCentral()
}

val internal: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val mockito: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

val mockitoRuntimeOnly: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
  extendsFrom(mockito)
}

dependencies {
  implementation(libs.checkerframework.qual)
  implementation(libs.jetbrains.annotations)
  implementation(libs.jspecify)
  implementation(libs.spotbugs.annotations)
  testImplementation(libs.hamcrest)
  testImplementation(libs.mockito)

  checkerFramework(libs.checkerframework)

  pitest(libs.arcmutate.base)
  pitest(libs.logback.classic)
  pitest(libs.pitest.accelerator.junit5)
  pitest(libs.pitest.git.plugin)
  pitest(libs.pitest.kotlin.plugin)
  pitest(libs.slf4j.api)

  internal(libs.junit.jupiter)
  internal(libs.pitest)
  internal(libs.pitest.junit5.plugin)
  internal(libs.puppycrawl.checkstyle)
  mockito(libs.mockito) {
      isTransitive = false
  }
}

java {
  withSourcesJar()
  withJavadocJar()
}

testing {
  suites {
    // Configure the built-in test suite
    val test by
        getting(JvmTestSuite::class) {
          // Use JUnit Jupiter test framework
          useJUnitJupiter(internal.dependencies.find { it.group == "org.junit.jupiter" }!!.version)
          targets {
            configureEach {
              testTask.configure {
                jvmArgs("-javaagent:${mockitoRuntimeOnly.asPath}")
              }
            }
          }
        }
  }
}

aylett { jvm { jvmVersion = 21 } }

configure<CheckerFrameworkExtension> {
  extraJavacArgs =
      listOf(
          // "-AcheckPurityAnnotations",
          "-AconcurrentSemantics",
      )
  checkers =
      listOf(
          "org.checkerframework.checker.nullness.NullnessChecker",
          "org.checkerframework.common.initializedfields.InitializedFieldsChecker",
      )
}

spotless {
  java {
    importOrder("", "java|javax|jakarta", "\\#", "\\#java|\\#javax|\\#jakarta").semanticSort()
    removeUnusedImports()
    eclipse().configFile("./config/eclipse-java-formatter.xml")
    formatAnnotations()
  }
  kotlinGradle {
    ktlint()
    ktfmt()
  }
}

checkstyle {
  toolVersion =
      internal.dependencies
          .find { it.group == "com.puppycrawl.tools" && it.name == "checkstyle" }!!
          .version!!
  maxWarnings = 0
}

tasks.withType(JavaCompile::class) { mustRunAfter(tasks.named("spotlessJavaApply")) }

tasks.named("check").configure { dependsOn(tasks.named("spotlessCheck")) }

val isCI = providers.environmentVariable("CI").isPresent

if (!isCI) {
  tasks.named("spotlessCheck").configure { dependsOn(tasks.named("spotlessApply")) }
}

val historyLocation = projectDir.resolve("build/pitest/history")

pitest {
  targetClasses.add("eu.aylett.*")

  junit5PluginVersion =
      internal.dependencies
          .find { it.group == "org.pitest" && it.name == "pitest-junit5-plugin" }!!
          .version
  verbosity = "NO_SPINNER"
  pitestVersion =
      internal.dependencies.find { it.group == "org.pitest" && it.name == "pitest" }!!.version
  failWhenNoMutations = false
  mutators = listOf("STRONGER", "EXTENDED")
  timeoutFactor = BigDecimal.TEN

  exportLineCoverage = true
  features.add("+auto_threads")
  if (isCI) {
    // Running in GitHub Actions
    features.addAll("+git(from[HEAD~1])", "+gitci(level[warning])")
    outputFormats = listOf("html", "xml", "gitci")
    failWhenNoMutations = false
  } else {
    historyInputLocation = historyLocation
    historyOutputLocation = historyLocation
    features.addAll("-gitci")
    outputFormats = listOf("html", "xml")
    failWhenNoMutations = true
  }

  jvmArgs.add("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

val pitestReportLocation: Provider<Directory> = project.layout.buildDirectory.dir("reports/pitest")

val printPitestReportLocation by
    tasks.registering {
      group = "verification"
      val location = pitestReportLocation.map { it.file("index.html") }
      doLast { println("Pitest report: file://${location.get()}") }
    }

tasks.named("pitest").configure { finalizedBy(printPitestReportLocation) }

publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/andrewaylett/arc")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}

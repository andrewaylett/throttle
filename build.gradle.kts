@file:Suppress("UnstableApiUsage")

import okio.ByteString.Companion.decodeBase64
import org.checkerframework.gradle.plugin.CheckerFrameworkExtension
import org.gradle.kotlin.dsl.configure

plugins {
  `java-library`
  `jvm-test-suite`
  `maven-publish`
  signing
  id("eu.aylett.conventions") version "0.5.2"
  id("eu.aylett.plugins.version") version "0.5.2"
  id("org.checkerframework") version "0.6.56"
  id("com.diffplug.spotless") version "7.2.1"
  checkstyle
  id("info.solidsoft.pitest") version "1.15.0"
  id("com.groupcdg.pitest.github") version "1.0.7"
  id("com.github.spotbugs") version "6.2.2"
  id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "eu.aylett"

version = aylett.versions.gitVersion()

val versionDetails = aylett.versions.versionDetails()

repositories {
  // Use Maven Central for resolving dependencies.
  mavenCentral()
}

val internal: Configuration by
    configurations.creating {
      isCanBeConsumed = true
      isCanBeResolved = false
    }

val mockito: Configuration by
    configurations.creating {
      isCanBeConsumed = true
      isCanBeResolved = false
    }

val mockitoRuntimeOnly: Configuration by
    configurations.creating {
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
  testImplementation(libs.guava.testlib)

  checkerFramework(libs.checkerframework)

  pitest(libs.arcmutate.base)
  pitest(libs.logback.classic)
  pitest(libs.pitest.accelerator.junit5)
  pitest(libs.pitest.git.plugin)
  pitest(libs.slf4j.api)

  internal(libs.junit.jupiter)
  internal(libs.pitest)
  internal(libs.pitest.junit5.plugin)
  internal(libs.puppycrawl.checkstyle)
  mockito(libs.mockito) { isTransitive = false }
}

java {
  withSourcesJar()
  withJavadocJar()
}

testing {
  suites {
    // Configure the built-in test suite
    @Suppress("unused")
    val test by
        getting(JvmTestSuite::class) {
          // Use JUnit Jupiter test framework
          useJUnitJupiter(internal.dependencies.find { it.group == "org.junit.jupiter" }!!.version)
          targets {
            configureEach {
              testTask.configure { jvmArgs("-javaagent:${mockitoRuntimeOnly.asPath}") }
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

tasks.withType(JavaCompile::class) { mustRunAfter(tasks.named("spotlessJavaCheck")) }

tasks.named("prepareKotlinBuildScriptModel").configure {
  mustRunAfter(tasks.named("spotlessKotlinGradleCheck"))
}

tasks.named("check").configure { dependsOn(tasks.named("spotlessCheck")) }

val isCI = providers.environmentVariable("CI").isPresent

if (!isCI) {
  tasks.named("spotlessJavaCheck").configure { dependsOn(tasks.named("spotlessJavaApply")) }
  tasks.named("spotlessKotlinGradleCheck").configure {
    dependsOn(tasks.named("spotlessKotlinGradleApply"))
  }
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

  jvmArgs.addAll(
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "-javaagent:${mockitoRuntimeOnly.asPath}",
  )
}

val pitestReportLocation: Provider<Directory> = project.layout.buildDirectory.dir("reports/pitest")

val printPitestReportLocation by
    tasks.registering {
      group = "verification"
      val location = pitestReportLocation.map { it.file("index.html") }
      doLast { println("Pitest report: file://${location.get()}") }
    }

tasks.named("pitest").configure { finalizedBy(printPitestReportLocation) }

val checkPublishVersion by
    tasks.registering {
      doNotTrackState("Either does nothing or fails the build")
      doFirst {
        val versionDetails = aylett.versions.versionDetails()
        if (!versionDetails.isCleanTag) {
          logger.error("Version details is {}", versionDetails)
          throw IllegalStateException(
              "Can't publish a plugin with a version (${versionDetails.version}) that's not a clean tag",
          )
        }
      }
    }

tasks.withType<PublishToMavenRepository>().configureEach { dependsOn(checkPublishVersion) }

publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/andrewaylett/throttle")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}

@Suppress("unused")
nexusPublishing {
  repositories {
    sonatype {
      username = System.getenv("OSSRH_TOKEN_USER")
      password = System.getenv("OSSRH_TOKEN_PASSWORD")
    }
  }
}

publishing.publications {
  @Suppress("unused")
  val mavenJava by
      creating(MavenPublication::class) {
        from(components["java"])
        pom {
          name.set("Throttle")
          description.set("Don't call a service when we know we'll overload it.")
          url.set("https://throttle.aylett.eu/")
          licenses {
            license {
              name.set("Apache-2.0")
              url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
          }
          developers {
            developer {
              id.set("aylett")
              name.set("Andrew Aylett")
              email.set("andrew@aylett.eu")
              organization.set("Andrew Aylett")
              organizationUrl.set("https://www.aylett.co.uk/")
            }
          }
          scm {
            connection.set("scm:git:https://github.com/andrewaylett/throttle.git")
            developerConnection.set("scm:git:ssh://git@github.com:andrewaylett/throttle.git")
            url.set("https://github.com/andrewaylett/throttle/")
          }
        }
      }
}

signing {
  setRequired({ gradle.taskGraph.hasTask(":publishMavenJavaPublicationToSonatypeRepository") })
  val signingKey: String? = System.getenv("GPG_SIGNING_KEY")?.decodeBase64()?.utf8()
  useInMemoryPgpKeys(signingKey, "")
  sign(publishing.publications)
}

@Suppress("unused")
val printCurrentVersion by
    tasks.registering {
      group = "version"
      doLast { println(versionDetails.lastTag) }
    }

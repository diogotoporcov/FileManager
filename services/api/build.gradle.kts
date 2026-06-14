import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
}

group = "com.filemanager"
version = "0.0.1-SNAPSHOT"

base {
    archivesName.set("filemanager-api")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

jacoco {
    toolVersion = "0.8.14"
}

val mockitoAgent by configurations.creating

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val generatedBenchmarkRunId = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        .format(OffsetDateTime.now(ZoneOffset.UTC)) + "-" + UUID.randomUUID().toString().substring(0, 8)

data class BenchmarkPythonResolution(val command: String, val source: String, val attemptedFallbacks: List<String>)

fun benchmarkPythonFallbacks(): List<String> {
    return if (System.getProperty("os.name").lowercase().contains("win")) {
        listOf("py", "python")
    } else {
        listOf("python3", "python")
    }
}

fun verifyPythonCommand(command: String): Boolean {
    return try {
        val process = ProcessBuilder(command, "--version")
                .redirectErrorStream(true)
                .start()

        process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}

fun resolveBenchmarkPythonExecutable(): BenchmarkPythonResolution {
    val propertyValue = providers.gradleProperty("benchmarkPythonExecutable").orNull
    if (!propertyValue.isNullOrBlank()) {
        if (verifyPythonCommand(propertyValue)) {
            return BenchmarkPythonResolution(propertyValue, "GRADLE_PROPERTY", emptyList())
        }
        throw GradleException(
                "Invalid benchmark Python executable. resolution source=GRADLE_PROPERTY, " +
                        "configured command=$propertyValue, fallback commands attempted=[]")
    }

    val environmentValue = providers.environmentVariable("BENCHMARK_PYTHON_EXECUTABLE").orNull
    if (!environmentValue.isNullOrBlank()) {
        if (verifyPythonCommand(environmentValue)) {
            return BenchmarkPythonResolution(environmentValue, "ENVIRONMENT", emptyList())
        }
        throw GradleException(
                "Invalid benchmark Python executable. resolution source=ENVIRONMENT, " +
                        "configured command=$environmentValue, fallback commands attempted=[]")
    }

    val attempted = mutableListOf<String>()
    for (candidate in benchmarkPythonFallbacks()) {
        attempted.add(candidate)

        if (verifyPythonCommand(candidate)) {
            return BenchmarkPythonResolution(candidate, "FALLBACK", attempted.toList())
        }
    }

    throw GradleException(
            "Invalid benchmark Python executable. resolution source=FALLBACK, configured command=<none>, " +
                    "fallback commands attempted=${attempted.joinToString(",")}")
}

val performanceTestSourceSet = sourceSets.create("performanceTest") {
    java.srcDir("src/performanceTest/java")
    resources.srcDir("src/performanceTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val benchmarkFrameworkTestSourceSet = sourceSets.create("benchmarkFrameworkTest") {
    java.srcDir("src/benchmarkFrameworkTest/java")
    resources.srcDir("src/benchmarkFrameworkTest/resources")
    compileClasspath += sourceSets.main.get().output + performanceTestSourceSet.output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[performanceTestSourceSet.implementationConfigurationName]
        .extendsFrom(configurations.testImplementation.get())
configurations[performanceTestSourceSet.runtimeOnlyConfigurationName]
        .extendsFrom(configurations.testRuntimeOnly.get())
configurations[benchmarkFrameworkTestSourceSet.implementationConfigurationName]
        .extendsFrom(configurations.testImplementation.get())
configurations[benchmarkFrameworkTestSourceSet.implementationConfigurationName]
        .extendsFrom(configurations[performanceTestSourceSet.implementationConfigurationName])
configurations[benchmarkFrameworkTestSourceSet.runtimeOnlyConfigurationName]
        .extendsFrom(configurations.testRuntimeOnly.get())
configurations[benchmarkFrameworkTestSourceSet.runtimeOnlyConfigurationName]
        .extendsFrom(configurations[performanceTestSourceSet.runtimeOnlyConfigurationName])

fun configureBenchmarkSystemProperties(task: Test, taskRecordCount: String?) {
    fun passGradleProperty(propertyName: String, systemPropertyName: String) {
        val value = providers.gradleProperty(propertyName)

        if (value.isPresent) {
            task.systemProperty(systemPropertyName, value.get())
            task.systemProperty("$systemPropertyName.source", "GRADLE_PROPERTY")
        }
    }

    if (taskRecordCount != null) {
        task.systemProperty("benchmark.records", taskRecordCount)
        task.systemProperty("benchmark.records.source", "TASK_DEFAULT")

        if (!providers.gradleProperty("benchmarkWarmupIterations").isPresent
                && !providers.environmentVariable("BENCHMARK_WARMUP_ITERATIONS").isPresent) {
            task.systemProperty("benchmark.warmup-iterations", if (taskRecordCount == "1000000") "10" else "20")
            task.systemProperty("benchmark.warmup-iterations.source", "TASK_DEFAULT")
        }

        if (!providers.gradleProperty("benchmarkMeasuredIterations").isPresent
                && !providers.environmentVariable("BENCHMARK_MEASURED_ITERATIONS").isPresent) {
            task.systemProperty("benchmark.measured-iterations", if (taskRecordCount == "1000000") "50" else "100")
            task.systemProperty("benchmark.measured-iterations.source", "TASK_DEFAULT")
        }
    }

    passGradleProperty("benchmarkRecords", "benchmark.records")
    passGradleProperty("benchmarkSeed", "benchmark.seed")
    passGradleProperty("benchmarkWarmupIterations", "benchmark.warmup-iterations")
    passGradleProperty("benchmarkMeasuredIterations", "benchmark.measured-iterations")
    passGradleProperty("benchmarkConcurrency", "benchmark.concurrency")
    passGradleProperty("benchmarkDuplicateDistribution", "benchmark.duplicate-distribution")
    passGradleProperty("benchmarkProfile", "benchmark.profile")
    passGradleProperty("benchmarkProfileFile", "benchmark.profile-file")
    passGradleProperty("benchmarkInstrumentationMode", "benchmark.instrumentation-mode")
    passGradleProperty("benchmarkBaselineCpu", "benchmark.baseline.cpu")
    passGradleProperty("benchmarkBaselineMemory", "benchmark.baseline.memory")
    passGradleProperty("benchmarkBaselineStorage", "benchmark.baseline.storage")
    passGradleProperty("benchmarkBaselineDockerResourceLimits", "benchmark.baseline.docker-resource-limits")

    val requestedRunId = providers.gradleProperty("benchmarkRunId")
            .orElse(providers.environmentVariable("BENCHMARK_RUN_ID"))

    task.systemProperty("benchmark.run-id", requestedRunId.getOrElse(generatedBenchmarkRunId))
    task.systemProperty("benchmark.run-id.source", if (requestedRunId.isPresent) "GRADLE_PROPERTY_OR_ENVIRONMENT" else "TASK_DEFAULT")
    task.systemProperty("benchmark.config-dir", rootProject.layout.projectDirectory.dir("benchmarks/config").asFile.path)
    task.systemProperty("benchmark.reports-dir", rootProject.layout.projectDirectory.dir("benchmarks/reports").asFile.path)
    task.systemProperty("benchmark.results-dir", rootProject.layout.projectDirectory.dir("benchmarks/results").asFile.path)

    task.doFirst {
        val python = resolveBenchmarkPythonExecutable()

        task.systemProperty("benchmark.python-executable", python.command)
        task.systemProperty("benchmark.python-executable.source", python.source)
        task.systemProperty("benchmark.python-executable.fallbacks-attempted", python.attemptedFallbacks.joinToString(","))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql:42.7.11")
    implementation("org.hibernate.orm:hibernate-vector:7.1.0.Final")
    implementation("io.minio:minio:8.6.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core:5.23.0") { isTransitive = false }

    add("performanceTestImplementation", "org.testcontainers:junit-jupiter:1.21.3")
    add("performanceTestImplementation", "org.testcontainers:postgresql:1.21.3")
    add("performanceTestImplementation", "org.postgresql:postgresql:42.7.11")
    add("performanceTestImplementation", "org.apache.commons:commons-csv:1.14.1")
    add("performanceTestRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

tasks.register<Test>("performanceTest") {
    description = "Runs benchmark correctness gates and measured repository-scale performance tests."
    group = "verification"
    testClassesDirs = performanceTestSourceSet.output.classesDirs
    classpath = performanceTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    outputs.upToDateWhen { false }
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
    maxHeapSize = providers.gradleProperty("benchmarkMaxHeap").getOrElse("4g")
    configureBenchmarkSystemProperties(this, null)
}

tasks.register<Test>("benchmarkFrameworkTest") {
    description = "Runs fast benchmark-framework utility tests without executing repository-scale benchmarks."
    group = "verification"
    testClassesDirs = benchmarkFrameworkTestSourceSet.output.classesDirs
    classpath = benchmarkFrameworkTestSourceSet.runtimeClasspath
    dependsOn("performanceTestClasses")
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
}

tasks.register("validateBenchmarkPythonExecutable") {
    description = "Validates the Python executable before local benchmark report generation."
    group = "benchmark"
    doLast {
        resolveBenchmarkPythonExecutable()
    }
}

fun registerRawBenchmarkTask(taskName: String, recordCount: String) {
    tasks.register<Test>(taskName) {
        description = "Seed and benchmark $recordCount synthetic file metadata and fingerprint records."
        group = "benchmark"
        testClassesDirs = performanceTestSourceSet.output.classesDirs
        classpath = performanceTestSourceSet.runtimeClasspath
        useJUnitPlatform()
        shouldRunAfter(tasks.test)
        outputs.upToDateWhen { false }
        jvmArgs("-javaagent:${mockitoAgent.asPath}")
        maxHeapSize = providers.gradleProperty("benchmarkMaxHeap").getOrElse("4g")
        configureBenchmarkSystemProperties(this, recordCount)
    }
}

registerRawBenchmarkTask("performanceTest10kRaw", "10000")
registerRawBenchmarkTask("performanceTest100kRaw", "100000")
registerRawBenchmarkTask("performanceTest1mRaw", "1000000")

fun registerBenchmarkReportTask(taskName: String, rawTaskName: String?, scales: String) {
    tasks.register<Exec>(taskName) {
        description = "Generates benchmark presentation artifacts for $scales."
        group = "benchmark"
        workingDir = rootProject.layout.projectDirectory.asFile
        dependsOn("validateBenchmarkPythonExecutable")
        if (rawTaskName != null) {
            dependsOn(rawTaskName)
        }

        doFirst {
            val python = resolveBenchmarkPythonExecutable()
            val requestedRunId = providers.gradleProperty("benchmarkRunId")
                    .orElse(providers.environmentVariable("BENCHMARK_RUN_ID"))
            val runId = requestedRunId.getOrElse(generatedBenchmarkRunId)

            commandLine(
                    python.command,
                    "benchmarks/scripts/generate-benchmark-report.py",
                    "--reports-dir",
                    "benchmarks/reports",
                    "--run-id",
                    runId,
                    "--scales",
                    scales)
        }
    }
}

registerBenchmarkReportTask("benchmarkReport10k", "performanceTest10kRaw", "10k")
registerBenchmarkReportTask("benchmarkReport100k", "performanceTest100kRaw", "100k")
registerBenchmarkReportTask("benchmarkReport1m", "performanceTest1mRaw", "1m")
registerBenchmarkReportTask("benchmarkReportAll", null, "10k,100k,1m")

fun registerBenchmarkLifecycle(taskName: String, reportTaskName: String) {
    tasks.register(taskName) {
        description = "Runs local raw benchmark execution and report generation."
        group = "benchmark"
        dependsOn(reportTaskName)
    }
}

registerBenchmarkLifecycle("benchmark10k", "benchmarkReport10k")
registerBenchmarkLifecycle("benchmark100k", "benchmarkReport100k")
registerBenchmarkLifecycle("benchmark1m", "benchmarkReport1m")

tasks.named("performanceTest100kRaw") {
    mustRunAfter("performanceTest10kRaw")
}
tasks.named("performanceTest1mRaw") {
    mustRunAfter("performanceTest100kRaw")
}
tasks.named("benchmarkReport100k") {
    mustRunAfter("benchmarkReport10k")
}
tasks.named("benchmarkReport1m") {
    mustRunAfter("benchmarkReport100k")
}
tasks.named("benchmarkReportAll") {
    mustRunAfter("benchmarkReport1m")
    mustRunAfter("performanceTest10kRaw", "performanceTest100kRaw", "performanceTest1mRaw")
}

tasks.register("benchmarkAll") {
    description = "Runs the three primary benchmark scales independently and generates one comparison."
    group = "benchmark"
    dependsOn("performanceTest10kRaw", "performanceTest100kRaw", "performanceTest1mRaw", "benchmarkReportAll")
}

tasks.register<Exec>("benchmarkReport") {
    description = "Regenerates benchmark reports from existing raw results."
    group = "benchmark"
    workingDir = rootProject.layout.projectDirectory.asFile
    dependsOn("validateBenchmarkPythonExecutable")

    doFirst {
        val python = resolveBenchmarkPythonExecutable()
        val requestedRunId = providers.gradleProperty("benchmarkRunId")
                .orElse(providers.environmentVariable("BENCHMARK_RUN_ID"))
        val command = mutableListOf(
                python.command,
                "benchmarks/scripts/generate-benchmark-report.py",
                "--reports-dir",
                "benchmarks/reports")

        if (requestedRunId.isPresent) {
            command.add("--run-id")
            command.add(requestedRunId.get())
        } else if (gradle.startParameter.taskNames.any { it.endsWith("benchmarkAll") }) {
            command.add("--run-id")
            command.add(generatedBenchmarkRunId)
        }

        commandLine(command)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

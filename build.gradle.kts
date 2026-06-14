tasks.register("benchmark10k") {
    group = "benchmark"
    description = "Seed and benchmark 10,000 synthetic file metadata and fingerprint records."
    dependsOn(":services:api:benchmark10k")
}

tasks.register("benchmarkFrameworkTest") {
    group = "verification"
    description = "Run fast benchmark-framework utility tests."
    dependsOn(":services:api:benchmarkFrameworkTest")
}

tasks.register("benchmark100k") {
    group = "benchmark"
    description = "Seed and benchmark 100,000 synthetic file metadata and fingerprint records."
    dependsOn(":services:api:benchmark100k")
}

tasks.register("benchmark1m") {
    group = "benchmark"
    description = "Seed and benchmark 1,000,000 synthetic file metadata and fingerprint records."
    dependsOn(":services:api:benchmark1m")
}

tasks.register("benchmarkAll") {
    group = "benchmark"
    description = "Run 10k, 100k, and 1m benchmarks independently and generate scale comparison inputs."
    dependsOn(":services:api:benchmarkAll")
}

tasks.register("benchmarkReport") {
    group = "benchmark"
    description = "Regenerate benchmark reports from existing raw results."
    dependsOn(":services:api:benchmarkReport")
}

rootProject.name = "JMCL"
include(
    "JMCL",
    "JMCLCore",
    "JMCLBoot"
)

val minecraftLibraries = listOf("HMCLTransformerDiscoveryService", "HMCLMultiMCBootstrap")
include(minecraftLibraries)

for (library in minecraftLibraries) {
    project(":$library").projectDir = file("minecraft/libraries/$library")
}

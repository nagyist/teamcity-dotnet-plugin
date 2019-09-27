package jetbrains.buildServer.agent

enum class CommandLineArgumentType {
    // commands
    Mandatory,

    // additinal arguments
    Secondary,

    // user custom arguments
    Custom,

    // added by runner
    Infrastructural
}
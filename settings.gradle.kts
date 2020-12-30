rootProject.name = "rSub"

include(
    ":rsub-core",
    ":rsub-server",
    ":rsub-client",

    ":connectors:rsub-ktor-websocket-connector-core",
    ":connectors:rsub-ktor-websocket-connector-server",
    ":connectors:rsub-ktor-websocket-connector-client",

    ":playground"
)

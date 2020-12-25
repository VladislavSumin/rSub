rootProject.name = "rSub"

include(
    ":rSubCore",
    ":rSubServer",
    ":rSubClient",

    ":connectors:rSubKtorWebSocketConnectorCore",
    ":connectors:rSubKtorWebSocketConnectorServer",
    ":connectors:rSubKtorWebSocketConnectorClient",

    ":playground"
)

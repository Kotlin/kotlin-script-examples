// IJ_MCP_AUTH_TOKEN: Settings | Tools | MCP Server of the IDE serving MCP.
// IJ_MCP_SERVER_PROJECT_PATH: a project open in that IDE; needed only with several projects open.
// Run command: IJ_MCP_AUTH_TOKEN=<...> IJ_MCP_SERVER_PROJECT_PATH=<...> ./gradlew -p jvm/mcp-kts runIde

@file:McpServer(
    name = "idea",
    url = "http://127.0.0.1:64342/sse",
    envHeaders = [
        "IJ_MCP_AUTH_TOKEN=IJ_MCP_AUTH_TOKEN",
        "IJ_MCP_SERVER_PROJECT_PATH=IJ_MCP_SERVER_PROJECT_PATH",
    ]
)

idea.searchText("main")

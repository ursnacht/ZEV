# Claude Code als ACP Agent in JetBrains einrichten

## Voraussetzungen

- **JetBrains IDE** Version 2025.3 oder neuer
- **JetBrains AI Plugin** (Version 253.30387.147+)
- **Node.js** und **npm** installiert
- **Claude Code** installiert
- Gültiger **Anthropic API Key** oder **Claude Max Subscription**

## Option 1: Über die ACP Agent Registry (einfachster Weg)

1. Öffne **Settings → Tools → AI Assistant → Agents**
2. Klicke auf **"Install From ACP Registry…"**
3. Suche nach **Claude Code** und klicke **Install**
4. Der Agent ist sofort im **AI Chat** Tool Window verfügbar

## Option 2: Manuelle Einrichtung

### 1. ACP-Adapter installieren

```bash
npm install -g @zed-industries/claude-agent-acp
```

### 2. Custom Agent in JetBrains hinzufügen

1. Öffne das **AI Chat** Tool Window
2. Klicke auf das **Drei-Punkte-Menü** (⋮)
3. Wähle **"Add Custom Agent"**
4. Die Datei `~/.jetbrains/acp.json` wird geöffnet

### 3. `acp.json` konfigurieren

```json
{
  "agents": [
    {
      "name": "Claude Code",
      "command": "claude-agent-acp",
      "args": [],
      "env": {
        "ANTHROPIC_API_KEY": "sk-ant-..."
      }
    }
  ]
}
```

> Alternativ kann der API Key als System-Umgebungsvariable gesetzt werden, dann ist der `env`-Block nicht nötig.

### 4. IDE neustarten

Nach dem Neustart steht der Agent im AI Chat zur Auswahl.

## Unterstützte Features

- **@-Mentions** für Kontext-Referenzen
- **Bild-Anhänge**
- **Tool-Aufrufe** mit Permission Prompts
- **Edit Review** (Änderungen prüfen)
- **Todo-Listen**
- **Interaktive & Hintergrund-Terminals**
- **Custom Slash Commands**
- **MCP Server Integration** vom Client

## Optionale Einstellungen

In den JetBrains AI Assistant Settings können zusätzlich aktiviert werden:

- **"Expose configured MCP servers"** - gibt dem Agent Zugriff auf konfigurierte MCP Server
- **"Expose IntelliJ MCP server"** - gibt dem Agent IDE-Funktionen wie Refactoring

## Links

- [JetBrains ACP Dokumentation](https://www.jetbrains.com/help/ai-assistant/acp.html)
- [ACP Agent Registry - JetBrains Blog](https://blog.jetbrains.com/ai/2026/01/acp-agent-registry/)
- [claude-agent-acp - GitHub](https://github.com/zed-industries/claude-code-acp)
- [Agent Client Protocol Spezifikation](https://agentclientprotocol.com/)

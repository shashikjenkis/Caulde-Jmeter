# Claude AI JMeter Plugin — Complete Setup Guide

## What This Plugin Does

When installed, it adds **"Claude AI Assistant"** to JMeter's **Tools** menu.
Opening it gives you 4 tabs:

| Tab | What it does |
|-----|-------------|
| Script Generator | Describe test in English → Claude generates full JMX |
| HAR Importer | Upload .har file → Claude extracts all HTTP calls + auto-detects correlations + generates JMX |
| Correlation Advisor | Paste JMX → Claude finds dynamic values (tokens, session IDs) + suggests extractors |
| Parameterization | Paste JMX → Claude finds hardcoded data → suggests CSV Data Set + ${variables} |

---

## Prerequisites

- Java 11 or higher
- Apache Maven 3.6+
- Apache JMeter 5.4.x / 5.5.x / 5.6.x
- Anthropic API key (get one at https://console.anthropic.com/api-keys)

---

## Step 1 — Build the JAR

```bash
# Clone or copy the project folder
cd claude-jmeter-plugin

# Build the fat JAR (bundles all dependencies)
mvn clean package -DskipTests

# Output:  target/claude-jmeter-plugin-all.jar
```

---

## Step 2 — Install into JMeter

### Option A: Drop-in (Simplest)
```bash
# Copy the JAR into JMeter's extension directory
cp target/claude-jmeter-plugin-all.jar  /path/to/jmeter/lib/ext/

# Restart JMeter
```

### Option B: Via JMeter Plugin Manager (if you package it for the registry)
1. Open JMeter → Options → Plugins Manager
2. Go to "Available Plugins" tab
3. Search for "Claude AI" (after publishing to the registry)
4. Install → Restart JMeter

> **Note**: Publishing to the official JMeter Plugin Manager registry requires submitting
> to https://github.com/undera/jmeter-plugins — until then, use Option A (drop-in).

---

## Step 3 — Open the Plugin

1. Launch JMeter
2. Go to menu: **Tools → Claude AI Assistant**
3. A new window opens with the 4 tabs

---

## Step 4 — Enter Your API Key

1. In the top bar, paste your Anthropic API key (`sk-ant-...`)
2. Click **Save** — the key is stored in Java Preferences (not in any file)
3. The status bar turns green: "API key saved ✓"

---

## How to Use Each Tab

### Tab 1: Script Generator
1. Type your test scenario in plain English:
   ```
   100 concurrent users, ramp up 60 seconds,
   POST /api/login with JSON {"username":"${USER}","password":"${PASS}"},
   extract JWT token from response,
   GET /api/dashboard with Bearer ${TOKEN},
   assert response time < 1500ms,
   run for 5 minutes
   ```
2. Choose script type (JMX / Groovy / BeanShell)
3. Click **Generate JMX ↗**
4. Copy or use "Insert into Test Plan"

---

### Tab 2: HAR Importer (Most Powerful)

**How to export a HAR file:**
- **Chrome**: DevTools (F12) → Network tab → Record → Right-click any request → "Save all as HAR with content"
- **Firefox**: DevTools → Network → Cog icon → "Save All as HAR"
- **Burp Suite**: Proxy → HTTP history → select requests → right-click → "Save selected items"
- **Fiddler**: File → Export Sessions → "HTTPArchive v1.2"

**Then in the plugin:**
1. Click **Browse HAR file...**
2. Select your .har file
3. Click **Analyze HAR + Generate JMX ↗**

Claude will return:
```
---SUMMARY---
Found 23 HTTP requests across 3 domains...

---CORRELATIONS---
1. authToken — found in POST /login response JSON $.token
   → Add JSON Extractor after Login request
   → Variable name: authToken
   → Use ${authToken} in Authorization header of subsequent requests

2. _csrf — found in login page HTML hidden input
   → Add CSS/JQuery Extractor after Home Page request
   → Expression: input[name=_csrf]
   → Attribute: value

---PARAMETERS---
1. username — hardcoded "john@example.com" in POST /login
   → Replace with ${username}
   → Create CSV Data Set: users.csv with column: username,password

---JMX---
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan ...>
  ... (complete test plan)
```

---

### Tab 3: Correlation Advisor
- Paste an existing JMX
- Click **Find Correlations ↗**
- Claude identifies every dynamic value that will break on replay and gives you the exact extractor config

### Tab 4: Parameterization
- Paste an existing JMX
- Click **Suggest Parameterization ↗**
- Claude finds hardcoded values and tells you exactly how to replace them with ${variables} and CSV files

---

## Project Structure

```
claude-jmeter-plugin/
├── pom.xml                          ← Maven build, shade plugin config
└── src/main/
    ├── java/com/claudeplugin/jmeter/
    │   ├── service/
    │   │   └── ClaudeApiService.java    ← All Claude API calls
    │   ├── har/
    │   │   └── HarParser.java           ← Parse .har files
    │   └── ui/
    │       ├── ClaudeAssistantPanel.java ← Main Swing UI (4 tabs)
    │       └── ClaudeMenuCreator.java    ← Registers in JMeter's Tools menu
    └── resources/
        └── META-INF/services/
            └── org.apache.jmeter.gui.plugin.MenuCreator  ← ServiceLoader descriptor
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Claude AI Assistant" not in Tools menu | Ensure JAR is in `lib/ext/` (not `lib/`), restart JMeter |
| API key not saving | Check Java user preferences are writable; try running JMeter as admin once |
| HAR parse error | Ensure HAR was exported as "v1.2" format; very large HARs (>10MB) may timeout — filter static assets in browser before exporting |
| "Claude API error 401" | API key is invalid or expired — regenerate at console.anthropic.com |
| "Claude API error 429" | Rate limit hit — wait 60 seconds and retry |
| Long wait for HAR analysis | Large HAR files with many requests take 30-60s — normal |

---

## Publishing to JMeter Plugin Manager (Optional)

To make the plugin available via the Plugin Manager search:

1. Fork https://github.com/undera/jmeter-plugins
2. Add entry to `site/src/json/plugins.json`:
```json
{
  "id": "claude-ai-assistant",
  "name": "Claude AI Assistant",
  "description": "AI-powered scripting, HAR import, correlation and parameterization using Anthropic Claude",
  "screenshotUrl": "https://yoursite.com/screenshot.png",
  "helpUrl": "https://yourrepo.com/README.md",
  "markerClass": "com.claudeplugin.jmeter.ui.ClaudeMenuCreator",
  "libs": {
    "claude-jmeter-plugin": "https://yourhost.com/releases/claude-jmeter-plugin-all.jar"
  }
}
```
3. Submit a pull request to the jmeter-plugins repo

---

## API Cost Estimate

| Action | Typical tokens | Approx cost |
|--------|---------------|-------------|
| Script from description | ~1,500 | ~$0.002 |
| HAR file analysis (20 requests) | ~4,000 | ~$0.005 |
| Correlation analysis | ~2,500 | ~$0.003 |
| Parameterization analysis | ~2,000 | ~$0.003 |

Costs are based on claude-sonnet-4 pricing. Very affordable for performance testing work.

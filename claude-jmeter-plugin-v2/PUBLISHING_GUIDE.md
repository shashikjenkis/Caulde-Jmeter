# Claude JMeter Plugin v2 — Publishing & Team Sharing Guide

## What's New in v2
- Tab 5: **JMeter Chat** — conversational Q&A with multi-turn history + quick question chips
- Tab 6: **Test Plan Optimizer** — deep JMX audit, health score (0–100), optimized JMX output

---

## OPTION 1: Share with Your Team (Easiest — No Server Needed)

### Step 1 — Build the JAR locally
```bash
cd claude-jmeter-plugin-v2
mvn clean package -DskipTests
# Creates: target/claude-jmeter-plugin-all.jar  (~8 MB)
```

### Step 2 — Share the JAR
Share the single `claude-jmeter-plugin-all.jar` file with your teammates via:
- Email attachment (it's ~8 MB)
- Shared Google Drive / OneDrive / SharePoint folder
- Internal Slack/Teams file share
- USB drive (if corporate network is air-gapped)

### Step 3 — Each teammate installs it
```bash
# They just copy the JAR to their JMeter lib/ext/ folder:
cp claude-jmeter-plugin-all.jar  /path/to/apache-jmeter-5.6.x/lib/ext/

# Restart JMeter
# Tools menu will now show: "Claude AI Assistant"
```

### Where is lib/ext/ on each OS?
| OS | Default JMeter path |
|----|---------------------|
| Windows | `C:\apache-jmeter-5.6.x\lib\ext\` |
| macOS   | `/Applications/apache-jmeter-5.6.x/lib/ext/` or `/opt/homebrew/opt/jmeter/libexec/lib/ext/` |
| Linux   | `/opt/apache-jmeter-5.6.x/lib/ext/` or `~/apache-jmeter/lib/ext/` |

> Each team member needs their own Anthropic API key.
> Free tier: $5 credit included. Get key at: https://console.anthropic.com/api-keys

---

## OPTION 2: Host on GitHub Releases (Recommended for Teams)

This gives your team a permanent download link and version history.

### Step 1 — Create a GitHub repo
```bash
cd claude-jmeter-plugin-v2
git init
git add .
git commit -m "Initial release v1.0.0"

# Create repo on github.com, then:
git remote add origin https://github.com/YOUR_USERNAME/claude-jmeter-plugin.git
git push -u origin main
```

### Step 2 — Build and create a release
```bash
mvn clean package -DskipTests

# On GitHub.com:
# 1. Go to your repo → "Releases" (right sidebar)
# 2. Click "Create a new release"
# 3. Tag: v1.0.0
# 4. Title: Claude AI JMeter Plugin v1.0.0
# 5. Drag and drop: target/claude-jmeter-plugin-all.jar
# 6. Publish release
```

### Step 3 — Share the download link with team
```
https://github.com/YOUR_USERNAME/claude-jmeter-plugin/releases/latest/download/claude-jmeter-plugin-all.jar
```

Anyone with this link can download and install. No GitHub account needed to download.

### Step 4 — Install script (share this with teammates)
```bash
# install.sh — run this to install the plugin
#!/bin/bash
JMETER_HOME="${JMETER_HOME:-/opt/apache-jmeter}"
JAR_URL="https://github.com/YOUR_USERNAME/claude-jmeter-plugin/releases/latest/download/claude-jmeter-plugin-all.jar"

echo "Downloading Claude JMeter Plugin..."
curl -L -o "$JMETER_HOME/lib/ext/claude-jmeter-plugin-all.jar" "$JAR_URL"
echo "Installed! Restart JMeter and go to Tools > Claude AI Assistant"
```

---

## OPTION 3: Publish to JMeter Plugin Manager (Official Registry)

This makes your plugin searchable inside JMeter's Plugin Manager UI.
Anyone can find it by searching "Claude" in the Plugin Manager.

### Prerequisites
- Your JAR must be publicly hosted (GitHub Releases works fine)
- You need a GitHub account

### Step 1 — Fork the plugins repo
```
https://github.com/undera/jmeter-plugins
```
Click "Fork" on GitHub.

### Step 2 — Add your plugin entry
Edit the file: `site/src/json/plugins.json`

Add this entry to the JSON array:
```json
{
  "id": "claude-ai-assistant",
  "name": "Claude AI Assistant",
  "description": "AI-powered JMeter scripting: HAR import, correlation detection, parameterization, test plan optimization, and chat assistant using Anthropic Claude",
  "helpUrl": "https://github.com/YOUR_USERNAME/claude-jmeter-plugin#readme",
  "screenshotUrl": "https://raw.githubusercontent.com/YOUR_USERNAME/claude-jmeter-plugin/main/screenshot.png",
  "markerClass": "com.claudeplugin.jmeter.ui.ClaudeMenuCreator",
  "versions": {
    "1.0.0": {
      "changes": "Initial release with 6 tabs: Script Generator, HAR Importer, Correlations, Parameterization, Chat, Optimizer",
      "downloadUrl": "https://github.com/YOUR_USERNAME/claude-jmeter-plugin/releases/download/v1.0.0/claude-jmeter-plugin-all.jar",
      "libs": {
        "claude-jmeter-plugin-all": "https://github.com/YOUR_USERNAME/claude-jmeter-plugin/releases/download/v1.0.0/claude-jmeter-plugin-all.jar"
      }
    }
  },
  "vendor": "YOUR_NAME",
  "tags": ["ai", "claude", "scripting", "har", "correlation", "parameterization"]
}
```

### Step 3 — Submit Pull Request
```bash
git checkout -b add-claude-plugin
git add site/src/json/plugins.json
git commit -m "Add Claude AI Assistant plugin"
git push origin add-claude-plugin
# Then open PR on GitHub
```

After the PR is merged (usually takes 1-2 weeks), the plugin appears in:
`JMeter → Options → Plugins Manager → Available Plugins → search "Claude"`

---

## OPTION 4: Internal Maven Repository (Corporate Teams)

If your company has Nexus or Artifactory, publish there instead:

```xml
<!-- Add to pom.xml -->
<distributionManagement>
  <repository>
    <id>internal-releases</id>
    <url>http://your-nexus:8081/repository/maven-releases/</url>
  </repository>
</distributionManagement>
```

```bash
mvn clean deploy
# Then teammates add to their local Maven and download
```

Or just share the JAR via your internal artifact store/S3 bucket.

---

## API Key Management for Teams

Each team member needs their own Anthropic API key. Here are the options:

### Individual keys (recommended for experiments)
- Each person signs up at console.anthropic.com
- Free $5 credit to start
- Key is saved locally in Java Preferences (not shared anywhere)
- Cost: ~$0.003–$0.005 per script generation (very cheap)

### Shared team key (for corporate use)
If you want everyone to share one key:
1. Create a team account on console.anthropic.com
2. Generate a project-scoped API key
3. Distribute the key to the team via your password manager (1Password, LastPass, etc.)
4. Each person pastes it into the plugin's API key field once

### Cost estimate for a team of 10
| Usage | Monthly cost |
|-------|-------------|
| 50 script generations/day | ~$4.50/month |
| 100 HAR analyses/day | ~$15/month |
| Heavy daily use | ~$30/month |

---

## Keeping the Plugin Updated

When you push a new version:

```bash
# 1. Update version in pom.xml
# 2. Build
mvn clean package -DskipTests

# 3. Create new GitHub Release with new JAR
# 4. Team members just download and replace the JAR in lib/ext/
```

For GitHub Releases, the `latest` URL always points to the newest release:
```
.../releases/latest/download/claude-jmeter-plugin-all.jar
```

So teammates can re-run the install script to update automatically.

---

## Quick Summary: What to Do Right Now

Since you're experimenting locally:

1. `mvn clean package -DskipTests`
2. Copy JAR to `jmeter/lib/ext/`
3. Open JMeter → Tools → Claude AI Assistant
4. When ready to share: upload JAR to GitHub Releases
5. Send teammates the download link + install instructions above

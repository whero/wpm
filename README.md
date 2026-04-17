[![PaperMC](https://img.shields.io/badge/PaperMC-1.21.1-blue)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-GPL2-green)](LICENSE)

# Whero Plugin Manager (WPM)

A lightweight in-game plugin manager for PaperMC servers. Search, install, update, and remove plugins directly from your server console or chat — no manual JAR downloads needed.

WPM supports **Hangar** (PaperMC's official plugin repository), **Modrinth**, **GitHub Releases**, and the **GeyserMC Downloads API** as plugin sources.

⚠️ This plugin is only created for my own use case and YMMV. Use at your own risk. ⚠️

🪲 The code is created quickly using AI assistance, bugs do exist. Use at your own risk. 🪲

## Features

- **Search** the Hangar or Modrinth repositories without leaving the game
- **Install** plugins from Hangar, Modrinth, GitHub Releases, or GeyserMC with a single command
- **Update** tracked plugins individually or all at once
- **List** tracked plugins with live update availability checks
- **Enable/disable** plugins by renaming their JAR (applied on next server restart)
- **Pin/unpin** plugins to hold them at their current version and skip automatic updates
- **Identify** untracked plugins already on your server and link them to Hangar, Modrinth, GitHub, or GeyserMC for future updates
- **SHA256 verification** on downloads for integrity checking
- Interactive clickable chat UI with hover tooltips

## Requirements

- Paper 1.21+ (or any fork based on it, e.g. Purpur)
- Java 21+

## Installation

1. Download `WheroPluginManager-x.x.x.jar` from the releases page
2. Place it in your server's `plugins/` folder
3. Start or restart the server

## Commands

All commands are under `/wpm` (alias: `/pluginmanager`). Requires the `wpm.admin` permission (default: op).

| Command | Description |
|---|---|
| `/wpm search <query>` | Search Hangar for plugins |
| `/wpm search modrinth <query>` | Search Modrinth for plugins |
| `/wpm install <slug>` | Install a plugin from Hangar |
| `/wpm modrinth <slug>` | Install from Modrinth |
| `/wpm github <owner/repo>` | Install from GitHub Releases |
| `/wpm geyser <project>` | Install from GeyserMC (geyser, floodgate) |
| `/wpm remove <name>` | Remove a tracked plugin |
| `/wpm disable <name>` | Disable a plugin on next server restart |
| `/wpm enable <name>` | Enable a previously disabled plugin on next server restart |
| `/wpm pin <name>` | Pin a plugin at its current installed version (skipped by update) |
| `/wpm unpin <name>` | Unpin a plugin so it is updated again |
| `/wpm update [name]` | Update one or all tracked plugins (pinned plugins are skipped) |
| `/wpm list` | List tracked plugins and check for updates |
| `/wpm info <slug>` | Show detailed Hangar plugin info |
| `/wpm identify` | Scan untracked plugins and match them to Hangar |
| `/wpm identify link <plugin> <source>` | Link an untracked plugin to a source (`hangar:slug`, `modrinth:slug`, `github:owner/repo`, or `geysermc:project`) |
| `/wpm reload` | Reload the configuration |

## Usage

### Installing a plugin

```
/wpm search ViaVersion
/wpm install ViaVersion
```

Or from Modrinth:

```
/wpm search modrinth chunky
/wpm modrinth chunky
```

Or from GitHub:

```
/wpm github ViaVersion/ViaVersion
```

For Geyser/Floodgate, use the dedicated GeyserMC source for proper build tracking:

```
/wpm geyser geyser
/wpm geyser floodgate
```

After installing, restart your server to load the new plugin.

### Tracking existing plugins

If you already have plugins installed that you want WPM to manage updates for:

```
/wpm identify
```

This scans all loaded plugins, searches Hangar for matches, and lets you click to link them. You can also link manually:

```
/wpm identify link ViaVersion hangar:ViaVersion
/wpm identify link Chunky modrinth:chunky
/wpm identify link SomePlugin github:owner/repo
/wpm identify link Floodgate geysermc:floodgate
```

### Checking for updates

```
/wpm list       # Shows all tracked plugins with update availability
/wpm update     # Updates all tracked plugins
/wpm update ViaVersion  # Update a specific plugin
```

## Configuration

Located at `plugins/WheroPluginManager/config.yml`:

```yaml
# Maximum number of results to show in search
max-search-results: 10

# Verify SHA256 hash when downloading from Hangar
verify-hash: true
```

## Permissions

| Permission | Description | Default |
|---|---|---|
| `wpm.admin` | Allows use of all WPM commands | op |

## Building from source

```bash
./gradlew build
```

The output JAR will be in `build/libs/`.

## Credits

- Modrinth plugin source support contributed by [@8BitBanana](https://github.com/8BitBanana) in [#1](https://github.com/whero/wpm/pull/1).

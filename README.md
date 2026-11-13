# Void Client 1.1
A client-side utility console for Minecraft Java 1.21.11, Fabric Loader 0.20.1 and Java 21. Windows desktop tools require .NET Framework 4.x. No admin install is needed.

## Install and launch
Extract the Windows x64 ZIP and run **Void Setup.exe**. The installer opens the Void launcher and adds a Start menu shortcut. The existing Void 1.21.11 instance appears in the list if it is already installed.

Choose **New instance**, give it a name, and select it. New instances start with an empty mods folder. Choose **Install Void utilities** to add the utility console and Fabric API to the selected instance. **Launcher settings** controls maximum game memory and whether the launcher remembers your last instance. Each instance has its own mods, worlds, settings, screenshots, and logs. **Browse mods** searches Modrinth or CurseForge before the game starts. Select a project and review the compatible file and dependency count before installing it. **Add JARs** imports local Fabric mods into the selection. Select an imported mod and use **Enable / disable** to change whether it loads on the next launch. Use Fabric 1.21.11 builds and install their dependencies. The in-game Modrinth and CurseForge menus install into the running instance.

In the default setup, **Sign in with Prism** opens Prism, where you can choose **Settings → Accounts → Add Microsoft**. **Play instance** then launches using the account selected in Prism. If you configure a Void application ID, choose **Sign in with Microsoft** to sign in through your browser. Choose an instance and **Play** to launch it directly. The first launch downloads Java and the Minecraft assets and libraries to a shared cache. Right bracket (]) opens the utility console in a world. Microsoft account also lets you sign out. Void protects the saved refresh token with Windows DPAPI for the current Windows user.

### Launch with Prism
Select a Void instance and choose **Launch with Prism** to use your Prism Minecraft account. This mode requires [Prism Launcher for Windows](https://prismlauncher.org/download/windows/). From the repository root, install the dependency with `winget import -i desktop/prism-dependency.json`. Open Prism once and add your Microsoft account in its account settings.

Void creates one entry in your Prism instances folder for each Void instance you launch. Both launchers use the same game folder through a Windows directory junction, so mod changes and worlds appear in both. Avoid running the same instance twice. Prism handles Java, memory settings, game libraries and Microsoft authentication for this launch. The handoff works without a Void application ID.

### Microsoft application setup
The publisher must register a Microsoft Entra public desktop application supporting personal Microsoft accounts, register the mobile/desktop redirect URI **http://localhost**, and obtain access to the Minecraft Java Edition game services. Enter the approved public application (client) ID under **Launcher settings → Microsoft application settings**, or set VOID_MICROSOFT_CLIENT_ID before starting Void. For distribution, place the approved ID in microsoft-app.txt beside Void Setup.exe. Setup configures it for players. This build ships without an application ID. Players sign in with an account that has a Java Edition profile. Authentication uses a loopback redirect, PKCE and state validation.
## Utility console
**Native combat modules** live in the Void utility menu. Sword aura attacks a visible nearby opposing player with a cooled-down sword. Crystal aura breaks nearby crystals closer to an opponent than to you and places crystals on the obsidian or bedrock block you aim at while holding crystals. Auto totem moves an inventory totem into your offhand. Each switch starts off. Cycle combat range between 2, 2.5, and 3 blocks. Crystal targeting uses distance rather than a blast-damage prediction. These modules require only Void and Fabric API.

**Utilities** toggles the individual HUD rows and offers coordinate copying and waypoint capture. The saved mark is scoped to the current world/server and dimension. Portal conversion rounds down at negative coordinates. Auto sprint, auto select tool and auto respawn start disabled. Auto tool selects a suitable hotbar tool when mining; it leaves that slot selected. Auto respawn skips the death screen when enabled.
**Appearance** cycles neon red, purple, blue and steel grey accents on black and moves the HUD between four corners. Choices persist under the instance's config folder.
**Modrinth** and **CurseForge** search compatible Fabric projects. Select a result to resolve a stable file; use **Project & dependencies** to review requirements. **Install verified JAR on restart** writes the file into mods after checking its published digest and Fabric metadata. Restart the game to load it. Dependencies of third-party mods must be installed separately. For updates, close the game and replace the previous mod yourself.
**CurseForge access:** set the user environment variable `VOID_CURSEFORGE_KEY` to your own approved CurseForge API key, then completely restart the launcher. Without a key, use the Website button to search and download manually. Some authors restrict third-party downloads; open the project page in that case. Only install mods and plugins you trust: they execute as Java code.

## Custom plugins
The sample compass is optional. Copy `payload/example-plugin-1.1.0.jar` into the instance's mods folder, restart, then toggle **Compass bearing**. Use Add JARs in the launcher to install the example into a selected instance.
Plugins are ordinary client-side Fabric mods with a `voidclient` entrypoint in fabric.mod.json and a dependency on voidclient 1.x. Implement `dev.voidclient.api.VoidPlugin`. Register HUD readouts or clickable actions using stable, namespaced IDs; return null from a readout to hide it temporarily. See the complete example-plugin subproject in the source archive. All registered modules appear in Utilities automatically. Use Fabric events for additional behaviors.

## Rebuild from source
On x64 Windows run `.\build.ps1` in PowerShell. The script fetches and verifies Gradle 9.2.1 and, if needed, a Temurin JDK 21. Fabric Loom resolves the pinned Minecraft mappings, Loader and Fabric API. The remapped distribution JARs land in build/libs and example-plugin/build/libs. Use `.\build.ps1 -Run` for a development game. Other platforms can build with JDK 21 and Gradle 9.2.1 using `gradle build`.
Build the Windows launcher and its embedded runtime with desktop/build-desktop.ps1 on x64 Windows with Python and pip available. Place the remapped client JAR, Fabric API 0.141.6+1.21.11 JAR, and the Fabric 0.20.1 launcher profile JSON (fabric-profile.json) in payload beside the two executables. Bundle the engine and payload folders with the executables.

## Removing Void
The installed launcher, instances, and shared game cache live in %LOCALAPPDATA%\Void Client. Back up any worlds you want to keep before removing an instance. The launcher shortcut is in Start Menu\Programs\Void Client.



## Native PvP and accessibility controls

Press **]** in a world to open Void. Combat includes Kill Aura, Crystal Aura and Auto Totem. Kill Aura defaults to swords and respects attack cooldown. Crystal Aura places on the obsidian or bedrock block under the crosshair and breaks nearby crystals. Settings control range, weapon, cooldown, crystal mode and delay. Module keybinds are individually assignable under Controls → Key Binds → Void Client.

Use **Utilities → Access** for low-health and low-durability alerts. Use **Settings** to configure alert sound, the health threshold, HUD placement, text spacing, opacity and trim. Each instance stores these choices in config/void-client.properties. The Void JAR runs independently of Meteor.

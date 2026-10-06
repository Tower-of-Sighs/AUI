# AUI 1.2.5.4 corresponding source for Earth

This source distribution contains the ApricityUI library used by the nine
Xaero's World Map: Earth 0.1.0 files. ApricityUI remains licensed under LGPL-2.1;
see the root `LICENSE`. Original author and copyright notices are retained.

The library adds native map terrain, cameras, model queues, GPU targets and
entity/head rendering. Its standalone Rhino bridge runs the map interface
without requiring KubeJS. Loader-specific implementations retain separate
source trees and build settings.

The `common/src/main` and selected target source trees include every source and
resource used by these libraries, including shared AUI runtime changes. They
are not presented as an isolated map-feature patch: other compiled AUI changes
are included because they are part of the corresponding source of the binary.

## Targets and build requirements

| AUI target directory | Earth target | Java |
| --- | --- | --- |
| `targets/forge-1.18.2` | Forge 1.18.2 | 17 |
| `targets/forge-1.19.2` | Forge 1.19.2 | 17 |
| `targets/forge-1.20.1` | Forge 1.20.1 | 17 |
| `targets/fabric-1.20.1` | Fabric 1.20.1 | 17 |
| `targets/fabric-1.21.1` | Fabric 1.21.1 | 21 |
| `targets/fabric-26.1` | Fabric 26.1.2 | 25 |
| `targets/neoforge-1.21.1` | NeoForge 1.21.1 | 21 |
| `targets/neoforge-26.1` | NeoForge 26.1.2 | 25 |
| `targets/neoforge-26.2` | NeoForge 26.2 | 25 |

Use the Gradle wrapper inside the chosen target directory, with `JAVA_HOME`
pointing to its required JDK. On Windows, for example:

```powershell
Set-Location targets/neoforge-26.2
.\gradlew.bat assemble --no-daemon
```

On Linux/macOS use `./gradlew assemble --no-daemon` in that target directory.
The binary is generated under the target's `build/libs`. Run `sourcesJar` with
the same wrapper to obtain the target source archive. Forge remapping,
Fabric remapping and modern official names follow the respective target build
configuration. The shared `prepareCommonSources` tasks apply the version-specific
source exclusions before compilation.

The declared loader, Minecraft, Rhino and publishing-plugin versions are in
each target's Gradle files and the root `gradle.properties`; do not substitute
another target's artifact based only on the AUI version number. Normal assembly
does not require publishing credentials or a private editor project.

## Rebuilding Earth with this library

Build the matching AUI target, then use Earth's corresponding target wrapper:

```powershell
.\gradlew.bat assemble --no-daemon -PauiJar=G:/path/to/ApricityUI-neoforge-26.2-1.2.5.4.jar
```

Earth checks the required map API and packages the supplied library. Source:
https://github.com/kltyton/XaeroWorldMapEarth

This distribution does not include Minecraft, Xaero's World Map, Xlib or other
proprietary game assets. Those dependencies are resolved by the build or installed
separately, subject to their own licenses.

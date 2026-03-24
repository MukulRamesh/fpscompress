# Compact Machines Dependency Setup

## Overview

Compact Machines is required for Devs 1, 2, and 4 to access the CM API for factory dimension integration. The dependency is configured as `compileOnly` (compile-time only) since we only need API access.

## Version Information

- **Mod**: Compact Machines
- **Version**: 7.0.81 (Release - Feb 14, 2026)
- **Minecraft**: 1.21.1 (compatible with 1.21.11)
- **Mod Loader**: NeoForge
- **JAR File**: `compactmachines-neoforge-7.0.81.jar`

## Setup Instructions

### Step 1: Download Compact Machines JAR

Choose one source:

**Option A: CurseForge**
- Go to: https://www.curseforge.com/minecraft/mc-mods/compact-machines/files
- Filter by: Minecraft 1.21.1 + NeoForge
- Download: `compactmachines-neoforge-7.0.81.jar`

**Option B: Modrinth**
- Go to: https://modrinth.com/mod/compact-machines/versions
- Find version 7.0.81 for Minecraft 1.21.1 (NeoForge)
- Download: `compactmachines-neoforge-7.0.81.jar`

### Step 2: Create libs Folder

```bash
cd fpscompress-template-1.21.11
mkdir libs
```

### Step 3: Place JAR in libs Folder

Move the downloaded JAR file to:
```
fpscompress-template-1.21.11/libs/compactmachines-neoforge-7.0.81.jar
```

### Step 4: Uncomment Dependency in build.gradle

Edit `build.gradle` around line 133 and uncomment:

```groovy
compileOnly files("libs/compactmachines-neoforge-7.0.81.jar")
```

**Optional**: For runtime testing in dev environment, also uncomment:
```groovy
localRuntime files("libs/compactmachines-neoforge-7.0.81.jar")
```

### Step 5: Refresh Dependencies

```bash
./gradlew --refresh-dependencies build
```

## Verification

After setup, verify the dependency is available:

```bash
./gradlew dependencies --configuration compileClasspath | grep compact
```

You should see the Compact Machines JAR listed in the classpath.

## Alternative: Automated Download Script

If needed, a script can be created to automatically download and place the JAR file. Let the project maintainer know if this is preferred.

## Troubleshooting

**Problem**: Build fails with "Could not find compact-machines"
- **Solution**: Ensure the JAR is in `libs/` folder with exact filename `compactmachines-neoforge-7.0.81.jar`
- **Solution**: Verify the dependency line in `build.gradle` is uncommented

**Problem**: Import errors in IDE
- **Solution**: Refresh Gradle project in your IDE (IntelliJ: Gradle > Reload, VSCode: Reload Window)
- **Solution**: Run `./gradlew --refresh-dependencies` from terminal

**Problem**: Runtime errors when testing mod
- **Solution**: Uncomment the `localRuntime` line in `build.gradle` to include CM in dev environment

## For Developers

### Which Devs Need This?

- **Dev 1 (Core Registry)**: Portal routing integration with CM dimensions
- **Dev 2 (Client Assets)**: May need CM block/texture references
- **Dev 4 (State Machine)**: Factory state management within CM dimensions

### Compact Machines API Overview

The JAR contains **181 classes** across **57 packages**. Key packages for FPSCompress integration:

#### Core Packages

**Base Package**: `dev.compactmods.machines`

```java
import dev.compactmods.machines.CompactMachines;
import dev.compactmods.machines.CMRegistries;
```

#### Dimension Management (Dev 1 & 4)

**Package**: `dev.compactmods.machines.dimension`

Key classes:
- `Dimension` - Core dimension reference/management
- `CompactDimensionTransitions` - Dimension transition handling
- `VoidAirBlock` - Void dimension block type

```java
import dev.compactmods.machines.dimension.Dimension;
import dev.compactmods.machines.dimension.CompactDimensionTransitions;
```

#### Room Management (Dev 1, 3 & 4)

**Package**: `dev.compactmods.machines.room`

Key classes:
- `RoomHelper` - Room utility methods
- `Rooms` - Room registry and blocks

**Subpackages**:
- `room.spatial` - Spatial management (grid allocation, coordinates)
- `room.spawn` - Spawn point management
- `room.capability` - Room capabilities (energy, items, fluids)
- `room.graph` - Room graph structure (nodes/edges)

```java
import dev.compactmods.machines.room.RoomHelper;
import dev.compactmods.machines.room.spatial.*;
import dev.compactmods.machines.room.capability.*;
```

#### Machine Blocks & Entities (Dev 1 & 2)

**Package**: `dev.compactmods.machines.machine`

Key classes:
- `Machines` - Machine registry (Blocks, Items, BlockEntities)
- `machine.block.CompactMachineBlock` - Base machine block
- `machine.block.BoundCompactMachineBlock` - Bound machine implementation
- `machine.block.UnboundCompactMachineBlock` - Unbound machine implementation
- `machine.capability.MachineCapability` - Machine capability system

```java
import dev.compactmods.machines.machine.Machines;
import dev.compactmods.machines.machine.block.*;
import dev.compactmods.machines.machine.capability.MachineCapability;
```

#### Data Management (Dev 3 & 4)

**Package**: `dev.compactmods.machines.data`

For SavedData persistence and room data tracking:

```java
import dev.compactmods.machines.data.manager.*;
```

#### Server-Side Services (Dev 1, 3 & 4)

**Package**: `dev.compactmods.machines.server.service`

Key classes:
- Server room registrars
- Room upgrade managers
- Player history tracking

```java
import dev.compactmods.machines.server.service.*;
```

#### Network/Teleportation (Dev 1)

**Package**: `dev.compactmods.machines.network`

For portal routing and resource transfer:

```java
import dev.compactmods.machines.network.*;
import dev.compactmods.machines.network.machine.*;
```

### API Usage Examples

#### Example 1: Room Spatial Allocation (Dev 3)

```java
import dev.compactmods.machines.room.spatial.*;
import dev.compactmods.machines.room.RoomHelper;

// Query room spatial information for factory placement
// (Exact API calls to be determined during implementation)
```

#### Example 2: Machine Capability Access (Dev 1)

```java
import dev.compactmods.machines.machine.capability.MachineCapability;
import dev.compactmods.machines.machine.block.BoundCompactMachineBlock;

// Access machine capabilities for routing
// (Exact API calls to be determined during implementation)
```

#### Example 3: Dimension Management (Dev 4)

```java
import dev.compactmods.machines.dimension.Dimension;
import dev.compactmods.machines.dimension.CompactDimensionTransitions;

// Manage dimension transitions for factory state changes
// (Exact API calls to be determined during implementation)
```

### Complete Package List

<details>
<summary>Click to expand all 57 packages</summary>

- `dev.compactmods.machines` (core)
- `dev.compactmods.machines.client` (client-side)
- `dev.compactmods.machines.client.command`
- `dev.compactmods.machines.client.config`
- `dev.compactmods.machines.client.creative`
- `dev.compactmods.machines.client.keybinds.room`
- `dev.compactmods.machines.client.machine`
- `dev.compactmods.machines.client.render`
- `dev.compactmods.machines.client.room`
- `dev.compactmods.machines.client.widget`
- `dev.compactmods.machines.command`
- `dev.compactmods.machines.command.argument`
- `dev.compactmods.machines.command.rooms`
- `dev.compactmods.machines.compat` (compatibility)
- `dev.compactmods.machines.compat.curios`
- `dev.compactmods.machines.compat.jade`
- `dev.compactmods.machines.compat.jei`
- `dev.compactmods.machines.data` (data management)
- `dev.compactmods.machines.data.manager`
- `dev.compactmods.machines.dimension` (dimensions)
- `dev.compactmods.machines.feature`
- `dev.compactmods.machines.gamerule`
- `dev.compactmods.machines.i18n` (localization)
- `dev.compactmods.machines.machine` (machines)
- `dev.compactmods.machines.machine.block`
- `dev.compactmods.machines.machine.capability`
- `dev.compactmods.machines.machine.item`
- `dev.compactmods.machines.mixin`
- `dev.compactmods.machines.mixin.impl`
- `dev.compactmods.machines.network` (networking)
- `dev.compactmods.machines.network.machine`
- `dev.compactmods.machines.network.room`
- `dev.compactmods.machines.player`
- `dev.compactmods.machines.room` (rooms)
- `dev.compactmods.machines.room.attachment`
- `dev.compactmods.machines.room.block`
- `dev.compactmods.machines.room.capability`
- `dev.compactmods.machines.room.graph`
- `dev.compactmods.machines.room.graph.edge`
- `dev.compactmods.machines.room.graph.node`
- `dev.compactmods.machines.room.spatial` (spatial management)
- `dev.compactmods.machines.room.spawn`
- `dev.compactmods.machines.room.ui.overlay`
- `dev.compactmods.machines.room.ui.upgrades`
- `dev.compactmods.machines.room.upgrade`
- `dev.compactmods.machines.room.upgrade.event`
- `dev.compactmods.machines.room.upgrade.example`
- `dev.compactmods.machines.room.upgrade.graph`
- `dev.compactmods.machines.room.wall`
- `dev.compactmods.machines.server`
- `dev.compactmods.machines.server.event`
- `dev.compactmods.machines.server.service`
- `dev.compactmods.machines.server.service.provider`
- `dev.compactmods.machines.shrinking`
- `dev.compactmods.machines.util` (utilities)
- `dev.compactmods.machines.util.codec`
- `dev.compactmods.machines.util.item`
- `dev.compactmods.machines.villager`

</details>

### Next Steps

1. **Explore the API**: Decompile the JAR or use IDE features to explore class structures
2. **Check CM Documentation**: Visit [Compact Machines Wiki](https://github.com/CompactMods/CompactMachines) for API details
3. **Plan Integration**: Determine exact API calls needed for FPSCompress portal routing and dimension management
4. **Interface Design**: Define `IPortalRouter`, `ISpaceManager`, etc. with CM integration points

### IDE Setup Recommendation

Add Compact Machines source JAR for better autocomplete and documentation:
- Check if Modrinth/CurseForge provides a sources JAR
- Configure your IDE to attach sources for better development experience

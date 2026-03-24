# FPSCompress Developer Branch Workflow

## Branch Structure

The project is organized into isolated feature branches for parallel development:

### Main Branch
- **`main`**: Stable integration branch for completed modules

### Developer Feature Branches

| Branch | Developer | Module | Key Components |
|--------|-----------|--------|----------------|
| `feature/dev1-core-registry` | Dev 1 | Core Registry & Block Shell | `machine_portal`, `input_proxy`, `output_proxy`, `IPortalRouter` interface, NeoForge capabilities |
| `feature/dev2-client-assets` | Dev 2 | Client Assets & DataGen | Textures (16x16 PNGs), DataGen providers, block states, models, lang files |
| `feature/dev3-spatial-manager` | Dev 3 | Spatial & Dimension Manager | `ISpaceManager` interface, spiral grid allocation, chunk loading, void dimension |
| `feature/dev4-state-machine` | Dev 4 | State Machine & Fractional Logic | `IMachineLogic` interface, state transitions, fractional production math (pure Java) |
| `feature/dev5-anticheat-scanner` | Dev 5 | Spatial Capability Scanner | `IAntiCheatScanner` interface, BlockEntity scanning, validation logic |

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/MukulRamesh/fpscompress.git
cd fpscompress
```

### 2. Checkout Your Feature Branch

```bash
# Example for Dev 1
git checkout feature/dev1-core-registry

# Or for other developers
git checkout feature/dev2-client-assets
git checkout feature/dev3-spatial-manager
git checkout feature/dev4-state-machine
git checkout feature/dev5-anticheat-scanner
```

### 3. Work Directory

All development happens in:
```
fpscompress-template-1.21.11/
```

Run Gradle commands from this directory:
```bash
cd fpscompress-template-1.21.11
./gradlew build
```

## Development Workflow

### Daily Workflow

1. **Start your work session:**
   ```bash
   git checkout feature/devX-your-module
   git pull origin feature/devX-your-module
   ```

2. **Make your changes** in the appropriate directories:
   - **Dev 1**: `src/main/java/.../portal/`
   - **Dev 2**: `src/main/resources/assets/`, `src/main/java/.../datagen/`
   - **Dev 3**: `src/main/java/.../spatial/`
   - **Dev 4**: `src/main/java/.../logic/`
   - **Dev 5**: `src/main/java/.../scanner/`

3. **Test your changes:**
   ```bash
   cd fpscompress-template-1.21.11
   ./gradlew build
   ./gradlew runClient  # Test in Minecraft
   ```

4. **Commit your work:**
   ```bash
   git add .
   git commit -m "Dev X: Brief description of changes"
   git push origin feature/devX-your-module
   ```

### Best Practices

#### ✅ DO:
- Work only on your assigned module
- Commit frequently with descriptive messages
- Test your code before pushing
- Use package structure: `com.mukulramesh.fpscompress.<module>/`
- Define interfaces first, implement later
- Add JavaDocs for public APIs
- Keep your branch up-to-date with main (if needed)

#### ❌ DON'T:
- Don't modify files outside your module's scope
- Don't merge `main` into your branch without coordinating
- Don't commit broken code (always test with `./gradlew build`)
- Don't commit the `build/` or `run/` directories (already in `.gitignore`)
- Don't commit the Compact Machines JAR (already in `.gitignore` for `libs/`)

### Commit Message Format

Use clear, descriptive commit messages:

```
Dev X: [Action] Brief description

Examples:
- Dev 1: Add IPortalRouter interface definition
- Dev 1: Implement MachinePortalBlockEntity with capabilities
- Dev 2: Add block textures for machine_portal
- Dev 2: Generate BlockStateProvider for portal blocks
- Dev 3: Implement spiral grid allocation algorithm
- Dev 3: Add SavedData persistence for room coordinates
- Dev 4: Define state machine transitions (BUILDING → SIMULATING)
- Dev 4: Implement fractional production accumulator
- Dev 5: Add BlockEntity capability scanner
- Dev 5: Implement anti-cheat validation logic
```

## Module Integration

### Interface-First Development

Each module exposes an interface that others consume:

```
Dev 1 (IPortalRouter) ←→ Dev 4 (IMachineLogic)
Dev 3 (ISpaceManager) ←→ Dev 1 (IPortalRouter)
Dev 5 (IAntiCheatScanner) ←→ Dev 4 (IMachineLogic)
Dev 2 (DataGen) → All modules (generates assets)
```

### Cross-Module Dependencies

If your module needs to use another module's interface:

1. **Wait for interface definition** to be committed
2. **Coordinate with other dev** before pulling their changes
3. **Create a mock implementation** if needed for testing

Example:
```java
// Dev 1 needs ISpaceManager from Dev 3
// Option 1: Wait for Dev 3 to push the interface
// Option 2: Create a mock for testing
public class MockSpaceManager implements ISpaceManager {
    @Override
    public BlockPos allocateFactory() {
        return new BlockPos(0, 100, 0); // Mock implementation
    }
}
```

## Merging to Main

### When to Merge

Only merge when your module is:
- ✅ Fully implemented
- ✅ Tested (builds successfully)
- ✅ Documented (JavaDocs added)
- ✅ Reviewed (code review completed)

### Merge Process

1. **Update your branch with latest main:**
   ```bash
   git checkout feature/devX-your-module
   git fetch origin
   git merge origin/main
   # Resolve any conflicts
   git push origin feature/devX-your-module
   ```

2. **Create a Pull Request** on GitHub:
   - Go to: https://github.com/MukulRamesh/fpscompress/pulls
   - Click "New Pull Request"
   - Base: `main` ← Compare: `feature/devX-your-module`
   - Add description of your changes
   - Request review from team

3. **After approval**, merge via GitHub UI

4. **Clean up** (optional):
   ```bash
   git checkout main
   git pull origin main
   # Your changes are now in main!
   ```

## Conflict Resolution

If you encounter merge conflicts:

1. **Identify conflict source** (which files?)
2. **Coordinate with other developer** who modified the same file
3. **Resolve conflicts manually:**
   ```bash
   # Edit conflicted files
   git add <resolved-files>
   git commit -m "Dev X: Resolve merge conflicts with Dev Y"
   ```

4. **Test after resolving:**
   ```bash
   ./gradlew build
   ```

## Package Structure

Recommended package organization:

```
src/main/java/com/mukulramesh/fpscompress/
├── portal/               # Dev 1: Core Registry
│   ├── IPortalRouter.java
│   ├── MachinePortalBlock.java
│   ├── MachinePortalBlockEntity.java
│   ├── InputProxyBlock.java
│   └── OutputProxyBlock.java
├── spatial/              # Dev 3: Spatial Manager
│   ├── ISpaceManager.java
│   ├── SpiralGridAllocator.java
│   └── RoomData.java
├── logic/                # Dev 4: State Machine
│   ├── IMachineLogic.java
│   ├── MachineState.java
│   └── FractionalProduction.java
├── scanner/              # Dev 5: Anti-Cheat Scanner
│   ├── IAntiCheatScanner.java
│   ├── CapabilityScanner.java
│   └── ValidationResult.java
└── datagen/              # Dev 2: DataGen
    ├── BlockStateGen.java
    ├── ItemModelGen.java
    └── LanguageGen.java
```

## Testing Your Module

### Unit Tests (Dev 4 especially)

```bash
# Run tests
./gradlew test
```

### In-Game Testing

```bash
# Launch client
./gradlew runClient

# Launch server
./gradlew runServer
```

### Data Generation (Dev 2)

```bash
./gradlew runData
```

## Help & Resources

- **Project Docs**: `CLAUDE.md` - Architecture and guidelines
- **Compact Machines API**: `COMPACT_MACHINES_SETUP.md` - CM integration guide
- **GitHub Issues**: https://github.com/MukulRamesh/fpscompress/issues
- **NeoForge Docs**: https://docs.neoforged.net/

## Quick Reference

```bash
# Check current branch
git branch

# Switch branches
git checkout feature/devX-your-module

# See what changed
git status
git diff

# Pull latest changes
git pull origin feature/devX-your-module

# Push your changes
git add .
git commit -m "Dev X: Description"
git push origin feature/devX-your-module

# Build project
cd fpscompress-template-1.21.11
./gradlew build

# Run Minecraft
./gradlew runClient
```

## Questions?

- Check `CLAUDE.md` for architecture details
- Check `notes.md` for developer assignments
- Open an issue on GitHub
- Coordinate with your team in your communication channel

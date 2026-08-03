# Target CI Auto-Discovery

`verify-common.yml` does not contain a fixed version matrix. When a shared-code or build-definition change triggers the workflow, it scans every direct child directory of `targets/` and builds every enabled target.

## Target Descriptor

Every `targets/<loader>-<minecraft-version>/` directory must contain both `gradlew.bat` and `ci.properties`. Discovery fails when either is missing, so adding a target cannot silently bypass the shared-code verification gate.

```properties
# ci.enabled defaults to true when omitted, but set it explicitly for reviewability.
ci.enabled=true
# Required. The Temurin JDK major version used by this target's Gradle build.
ci.java=21
# Optional. Network-related Gradle failures are retried; allowed values are 1 through 5.
ci.attempts=3
```

Set `ci.enabled=false` only for a deliberately archived or temporarily unsupported target. Its descriptor and wrapper are still required and checked, but it is excluded from the CI build matrix.

## Adding A Target

1. Add `targets/<name>/` as an independent Gradle project with its own wrapper and a source mapping to `../../common`.
2. Add `ci.properties`, choosing the JDK required by that target.
3. Run `scripts/discover-targets.ps1` from the repository root and confirm the new target appears in the JSON matrix.
4. Build that target locally with `scripts/build-target.ps1 -Target <name>` using the declared JDK.
5. Merge the descriptor with the new target. The next shared-code CI run includes it automatically.

## Local Commands

```powershell
# Print the exact matrix CI will use.
.\scripts\discover-targets.ps1

# Build any single target without editing a hardcoded script list.
.\scripts\build-target.ps1 -Target fabric-1.20.1
```

The preset targets are examples only. `ci.properties` is the source of truth for CI eligibility, JDK selection, and retry count.

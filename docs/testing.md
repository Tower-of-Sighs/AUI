# Testing

ApricityUI has three test layers because the DOM/layout code can run without a
Minecraft window, while font rasterisation, item registries, and GL-backed
controls cannot.

## Commands

```powershell
# Fast deterministic JVM/headless suite.
.\gradlew.bat test --console plain --no-daemon

# Headless suite plus the report/classification gate.
.\gradlew.bat verifyAuiTestMatrix --console plain --no-daemon

# Forge client runtime smoke suite. The task requires a PASS marker written by
# the client; a process that starts and exits without assertions is a failure.
.\gradlew.bat clientTest --console plain --no-daemon

# Both layers, in order.
.\gradlew.bat testMatrix --console plain --no-daemon
```

`test` is allowed to skip tests that explicitly declare a live-client
capability. The complete list and the reason are written to
`build/reports/aui/test-summary.txt`. `verifyAuiTestMatrix` rejects any future
skip that is not classified as either a live Minecraft client or a required
Minecraft runtime class, and compares the result with
`src/test/resources/aui-client-only-tests.txt`.

Use `-PauiTestRequireNoSkips` when diagnosing a local environment that is
expected to provide every runtime dependency. This intentionally fails the
headless task instead of silently treating client-only tests as complete.

The client layer currently covers the runtime HTML/lifecycle smoke fixtures.
The headless suite owns DOM, layout, style, scrolling, select, and Slot
expression/selector behavior; only tests that genuinely exercise a live client
or GL-backed runtime should be added to the client-only inventory.

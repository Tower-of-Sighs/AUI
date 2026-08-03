# Publishing

Each target applies `gradle/target-conventions/publish.gradle`. The shared script publishes the target's Java component to the Sighs Maven repository and derives its Maven coordinates from that target:

```text
groupId:    mod_group_id
artifactId: <mod_name>-<loader>-<minecraft-version>
version:    mod_version
```

A version containing `SNAPSHOT` is sent to `maven-snapshots`; every other version is sent to `maven-releases`. Set credentials only in the publishing shell or CI secret store:

```powershell
$env:SIGHS_PUBLISH_USER = '<username>'
$env:SIGHS_PUBLISH_PASSWORD = '<password>'
```

Build or publish one target from its own directory with the JDK declared in its `ci.properties`:

```powershell
cd targets\fabric-1.20.1
.\gradlew.bat publish
```

The generated POM intentionally omits the target build classpath. Minecraft, loader, mappings, and `common` are packaged or supplied by the runtime rather than exposed as Maven dependencies.

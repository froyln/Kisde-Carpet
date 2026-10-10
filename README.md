<div align="center">

# Bullet Carpet

**A Minecraft 1.12.2 Carpet server mod with optional server-authoritative schematic and inventory integrations.**

[![Minecraft 1.12.2](https://img.shields.io/badge/Minecraft-1.12.2-green)](docs/usage/server-integrations.rst)
[![Java 8](https://img.shields.io/badge/Java-8-blue)](docs/usage/server-integrations.rst)

</div>

---

## Features

- Carpet rules for server-controlled Litematica capture and paste, plus read-only Tweakeroo inventory previews.
- Existing Bullet Carpet commands, rules, logging and Prometheus integration.
- A separate LiteLoader companion provides the client integrations; client classes and dependencies are not part of the server jar.

## Getting started

1. Use a Minecraft 1.12.2 server and Java 8.
2. Build the patched server distribution with `./gradlew createRelease`.
3. Install the generated `build/distributions/Carpetmod_dev.zip` contents alongside the matching Carpet server setup.
4. In the companion client project root, run `./gradlew build` and install `build/libs/Bullet-Carpet.litemod` in a LiteLoader 1.12.2 client with the pinned masa mods. The companion source is maintained separately from this server repository.
5. Enable server-side access only for trusted operators. The integration rules below default to `false`:

   ```text
   /carpet schematicSave true
   /carpet schematicPaste true
   /carpet schematicPasteNoUpdates true
   /carpet pcaProtocol true
   ```

`schematicPasteNoUpdates` suppresses paste-generated physics and redstone updates; later normal world ticking continues. Capture and paste also require operator permission and creative mode. Inventory preview requires operator permission and is read-only. See [integration documentation](docs/usage/server-integrations.rst) for pinned versions, limits, supported data and current verification status.

## Documentation

- [Server integrations](docs/usage/server-integrations.rst)
- [Carpet installation](docs/usage/install.rst)
- [Carpet manual](docs/index.rst)

## Building from source

Run `./gradlew setupCarpetmod` to generate the source tree, then make source changes and run `./gradlew createRelease`. This task creates an obfuscated patch archive under `build/distributions/`. The generated `src/` tree can be overwritten by setup tasks; preserve local edits before syncing.

The separate companion client project targets LiteLoader and Java 8. Its build is `./gradlew build --offline` when pinned dependencies are already cached.

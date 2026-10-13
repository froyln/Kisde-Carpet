Server-controlled client integrations
====================================

Kisde-Carpet adds rules for optional integrations with the separate
LiteLoader companion client mod. The inventory and no-update options are disabled by default. Enable only the
options needed on a server whose operators and creative-mode users are
trusted.

Rules and access
----------------

* ``schematicSave`` includes container inventories in server captures. When
  false, capture remains available without inventory contents or loot tables.
  Blocks, sign text and other block-entity metadata are still captured.
* ``schematicPaste`` restores schematic container inventories. When false,
  paste remains available: new containers are empty and existing same-block
  containers retain their contents. Other block-entity metadata is still
  pasted. A paste can be partial on failure or cancellation and has no undo.
* ``schematicPasteNoUpdates`` suppresses paste-generated block-added, neighbor,
  observer and inventory comparator updates independently of inventory paste.
  It defaults to false and retains the same operator/creative authorization.
* ``pcaProtocol`` permits an operator to preview nearby server container
  inventories in Tweakeroo and Litematica. It is read-only and does not include
  player inventories or Ender Chest account data.

For example, an administrator can enable the rules with Carpet's rule command::

   /carpet schematicSave true
   /carpet schematicPaste true
   /carpet schematicPasteNoUpdates true
   /carpet pcaProtocol true

Capture and paste additionally require creative mode. With no Kisde-Carpet server, the companion leaves Litematica’s ordinary native paste path available. A vanilla dedicated-server test placed block states but did not preserve container inventories or other tile-entity NBT; inventory-preserving paste requires the paired Kisde-Carpet server with ``schematicPaste`` enabled and the required authorization. The server validates
client data, checks access again while operations execute, and does not trust
client-supplied positions, dimensions, NBT or capabilities. Requests are limited
to loaded chunks and validated packet sizes. Capture
responses have size limits; paste has no 32,768-block or 16 MiB total-operation
cutoff. Paste follows Litematica's configured command rate; network bursts do
not abort an authorized operation.

Client setup
------------

Use Minecraft 1.12.2, Java 8, LiteLoader and the pinned masa compatibility
versions: Litematica 0.31.4, MiniHUD 0.41.1, Tweakeroo 0.31.0 and MaLiLib
0.54.0. Build the companion project with ``./gradlew build`` and install its
``Kisde-Carpet.litemod`` in the client's ``mods/1.12.2`` directory. The server
remains a separate Carpet jar mod; do not install client classes or dependencies
on the server.

The companion automatically detects a Kisde-Carpet server; no hidden client
property is required for assisted paste. In Litematica's save screen, Auto and
Server capture server data. The Save inventories option is off and disabled
when ``schematicSave`` is false; when true it can be toggled. Sign text and
other metadata remain available independently. Rule changes update an open
save screen. Client explicitly uses the ordinary local capture. In-memory capture also uses server
data when connected to Kisde-Carpet. The save screen shows the supported
options and disables unsupported entity and tick capture.

Inventory overlays refresh server contents about every 300 ms while rendered.
Litematica's schematic inventory remains the saved inventory; its world
inventory shows current server contents. Disabling ``pcaProtocol`` clears the
server preview.

Paste obeys Litematica's ``pasteReplaceBehavior`` setting: ``none`` preserves
existing blocks, including their inventories. Select ``all`` to restore NBT in
existing containers and signs, even when their block state already matches.
A fully filtered paste reports zero updates. All vanilla 1.12.2 block-entity types are accepted when their NBT type matches
the pasted block, including Ender Chests, beds, spawners, command/structure
blocks, portals and moving pistons. Invalid block/type combinations are
rejected; sign ``run_command`` actions remain rejected.

Enable ``schematicPasteNoUpdates`` to place blocks without triggering neighbor
physics, observer pulses, fluid block-added scheduling or inventory comparator
updates. Block and tile data are still sent to clients and saved. This does not
freeze later normal ticks, existing scheduled ticks or player-generated
updates. Moving pistons and other ticking block entities can continue their
normal behavior after placement. The rule affects the Kisde-Carpet assisted
paste path, not ordinary ``/setblock`` or other mods' placement paths.

Capture uses Litematica's AllAtOnce server-save flow. The supported capture
scope is blocks and block entities; entity and scheduled-tick data are not
included. The native client reader has a 2 MiB NBT accounting limit, so large
captures can be rejected. Paste uses the client's normal region, layer,
replacement and transform traversal, but operations may stop after applying a subset if the connection or server
policy changes. High paste rates can cause server lag; use Litematica's command
rate setting to pace large jobs.

MiniHUD structure data uses Carpet's existing ``carpet:client`` protocol. The
companion requests updates when MiniHUD's structure-box renderer is enabled.
Overworld, Nether and End structure marker data was received and decoded in
the pinned setup, with stale data cleared on dimension change and disconnect
and fresh data restored on reconnect. Carpet Client v1.4d coexisted without the
prior packet parsing failure. The companion copies ``carpet:client`` payloads
for LiteLoader listeners and avoids duplicate split-fragment processing inside
MaLiLib. On the final tested artifact, End City and Nether Fortress outlines
were visible in matched enabled/disabled framebuffer captures. The Overworld Witch Hut box also rendered and disappeared when disabled; the
disposable world terrain at its reported bounds did not resemble the structure,
so that test establishes rendering/toggle behavior but not terrain integrity.

Shared schematic storage is controlled separately by ``schematicShare`` and
does not require operator permission to upload. Authors and operators manage
shares. See :doc:`shared-schematics` for setup, placement behavior and limits.

Verification status
-------------------

The implementation has passed Java 8 server and client builds, a server
schematic packing check, live single- and multi-region captures (including a
reversed region axis), and a nine-container capture→paste→restart round trip
using an actual Litematica placement task. The captured
fixture reopened with nine inventory tiles, including nested shulker item NBT.
Paste transforms, layer filtering, replacement policies, cancellation,
revocation, disconnect cleanup and restart persistence passed their recorded
fixtures. Oversized capture responses are rejected while preserving the prior
file; the exact 2 MiB boundary remains untested. Tweakeroo preview fidelity,
range and authorization checks, timeout/stale-response cleanup, minecart target
refresh/removal and rapid target switching passed recorded live tests. MiniHUD End City, Nether Fortress and Witch Hut outlines rendered on the final
artifact. The Witch Hut bounds did not match the disposable world terrain, so
that test confirms rendering and toggle behavior, not terrain identity.

These results are feature-focused regression evidence, not a complete
compatibility certification. Two-client authorization isolation and bounded
near-cap workloads passed, as did final revision-78 Carpet Client channel
coexistence. Maximum-player scaling and the exact 2 MiB capture boundary remain
untested. The vanilla native paste path was tested for block-state placement;
it does not preserve tile-entity inventory NBT.

Administrative player inventory editing
---------------------------------------

``/view inv <player>`` and ``/view echest <player>`` require command permission
level 2 and the ``commandInventory`` rule. Online views refresh from the owner
before each inventory click, and close when the owner disconnects.

Only one offline edit session can be open for a player at a time. Closing it
merges the edited inventory with current player data. If the player joins during
the edit, the editor closes and its inventory changes transfer to the joining
player. If current offline player data cannot be reloaded, the command reports
that the changes were not saved and avoids writing a stale player snapshot.

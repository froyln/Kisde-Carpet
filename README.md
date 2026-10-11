<div align="center">

# Kisde-Carpet

**A Minecraft 1.12.2 Carpet server with multiple technical tools and rules.**

[![Minecraft 1.12.2](https://img.shields.io/badge/Minecraft-1.12.2-green)](docs/usage/server-integrations.rst)
[![Java 8](https://img.shields.io/badge/Java-8-blue)](docs/usage/server-integrations.rst)

</div>

---

## Features

- Carpet rules for server-controlled Litematica capture and paste, plus read-only Tweakeroo inventory previews.
- Existing Carpet commands, rules, logging and Prometheus integration.
- Imported Vastech rules and cluster tools for technical builds and server experiments.
- A separate LiteLoader companion provides the client integrations; client classes and dependencies are not part of the server jar.

Based on [Bullet Carpet](https://github.com/Dioswilson/Bullet-carpet), preserving its Minecraft 1.12.2 server lineage. Includes selected rules and tools from [Carpet Vastech Addition](https://github.com/Void-Skeleton/Carpet-Vastech-Addition), credited with the `vastech` category. Bullet and Kisde additions use the `bullet` and `kisde` categories. Companion source: [Kisde-Carpet-ClientSide](https://github.com/froyln/Kisde-Carpet-ClientSide).

## Getting started

1. Use a Minecraft 1.12.2 server and Java 8.
2. Build the patched server distribution with `./gradlew createRelease`.
3. Install the generated `build/distributions/Kisde-Carpet_dev.zip` contents alongside the matching Carpet server setup.
4. In the companion client project root, run `./gradlew build` and install `build/libs/Kisde-Carpet.litemod` in a LiteLoader 1.12.2 client with the pinned masa mods. The companion source is maintained separately from this server repository.
5. Enable server-side access only for trusted operators. The integration rules below default to `false`:

   ```text
   /carpet schematicSave true
   /carpet schematicPaste true
   /carpet schematicPasteNoUpdates true
   /carpet pcaProtocol true
   ```

`schematicPasteNoUpdates` suppresses paste-generated physics and redstone updates; later normal world ticking continues. Capture and paste also require operator permission and creative mode. Inventory preview requires operator permission and is read-only. See [integration documentation](docs/usage/server-integrations.rst) for pinned versions, limits, supported data and current verification status.

## Available rules

All 249 registered server rules are listed below. Defaults are the initial values before `carpet.conf` overrides. Use `/carpet <rule>` for details and accepted values, `/carpet <rule> <value>` to change a rule, and `/carpet setDefault <rule> <value>` to persist it.

The `bullet`, `kisde` and `vastech` categories identify additions from each project; other categories describe their purpose. See [Vastech technical tools](docs/usage/vastech-tools.rst) for cluster commands and imported rule behavior.

<details>
<summary>Show all rules, defaults and categories</summary>

| Rule | Default | Categories | Description |
| --- | --- | --- | --- |
| `1.8Spawning` | `false` | experimental | Using old 1.8 spawning rules: always 4 mobs per pack and honoring entity collisions while spawning |
| `accurateBlockPlacement` | `false` | creative | Allows to place blocks in different orientations. Requires Carpet Client |
| `alwaysSetPlayerIntoSurvival` | `false` | experimental, bullet | Always sets player gamemode into survival when using /s command |
| `antiCheatSpeed` | `false` | creative, survival | Prevents players from rubberbanding when moving too fast |
| `asyncBeaconUpdates` | `false` | creative | Beacons send out async block updates when powered |
| `asyncPacketUpdatesFix` | `false` | fix | Fixes the async packet bugs related to asynch observer updates. |
| `autocrafter` | `false` | feature | Turns crafting tables into automated crafting tables with inventorys. |
| `autoCraftingDropper` | `false` | feature | Auto-crafting dropper |
| `bedrockDropsAsItem` | `false` | experimental | Allows bedrock to drop as bedrock item if broken, similar to 1.8 and lower versions. |
| `blockCollisionsOptimization` | `false` | experimental | Optimized entity-block collision calculations. By masa |
| `blockEventSerializer` | `false` | fix | Saves the block event on server shutdown and loads at server startup. |
| `blockPlacementIgnoreEntity` | `false` | creative, kisde | Creative players can place blocks inside entities. |
| `blockStateSyncing` | `false` | fix, experimental, bullet | Fixes block states in F3 debug mode not updating for hoppers, droppers and dispensers. |
| `boundingBoxFix` | `false` | fix | Structure bounding boxes (i.e. witch huts) will generate correctly |
| `breedingMountingDisabled` | `false` | fix | Prevents players from mounting animals when holding breeding food |
| `cactusCounter` | `false` | commands, creative, survival | Items thrown into a cactus will count items that are destroyed in them. |
| `cakeAlwaysEat` | `false` | creative | Allows to always be able to eat cakes. |
| `calmNetherFires` | `false` | experimental | Permanent fires don't schedule random updates |
| `cameraModeDisableSpectatePlayers` | `false` | commands | Disables players in /c from spectating other players |
| `cameraModeRestoreLocation` | `false` | commands | Places players back to the original location when using camera mode by using /c then /s |
| `cameraModeSurvivalRestrictions` | `false` | commands | Restricts the use of /c in survival mode. |
| `carefulBreak` | `false` | feature | Places the mined block in the player inventory when sneaking. |
| `carpets` | `false` | survival | Placing carpets may issue carpet commands for non-op players |
| `castSuppressorCrashFix` | `false` | fix, vastech | Converts class cast errors in suppression guarded block updates into suppression exceptions |
| `chorusFruitShootable` | `false` | feature | Chorus fruit can be shot with an arrow to have it pop as an item as in the future minecraft versions. |
| `chunkDebugTool` | `false` | creative | Enables chunk debug on carpet client. |
| `combinePotionDuration` | `0` | feature | Combines the duration of potions when drinking out of a bottle. The combined duration capped by this carpet rule in gameticks. |
| `combineXPOrbs` | `false` | creative | XP orbs combine with other into bigger orbs |
| `commandAutosave` | `true` | commands | Enables /autosave command to query information about the autosave and execute commands relative to the autosave |
| `commandBlockInfo` | `true` | commands | Enables /blockinfo command |
| `commandCameramode` | `true` | commands | Enables /c and /s commands to quickly switch between spectator and server modes |
| `commandChunk` | `true` | commands | Enables /chunk command |
| `commandCluster` | `false` | commands, creative, vastech | Enables OP cluster calculation, loading and creative loader construction |
| `commandDistance` | `true` | commands | Enables /distance command to measure in game distance between points |
| `commandEnderchest` | `false` | experimental, commands, bullet | Enables /enderchest <Player> to replace objective enderchest with your own one(Only for OPs) |
| `commandEntityInfo` | `true` | commands | Enables /lazychunkbehavior command |
| `commandFillBiome` | `true` | commands | Enables /fillbiome command to change the biome of an area |
| `commandGrow` | `true` | commands | Enables /grow command for growing plants |
| `commandInventory` | `false` | experimental, commands, bullet | Enables /view (inv&#124;echest) <playername> to view objective and manipulate enderchest/invenory |
| `commandLagspike` | `false` | commands | Enables /lagspike for manually induced lag spikes |
| `commandLazyChunkBehavior` | `false` | commands | Enables /entityinfo command |
| `commandLifeTime` | `true` | commands, kisde | Enables /lifetime entity lifetime tracking and filters |
| `commandLight` | `true` | commands | Enables /light for changing light levels |
| `commandLoadedChunks` | `true` | commands | Enables /loadedChunks command |
| `commandLog` | `true` | commands | Enables /log command to monitor events in the game via chat and overlays |
| `commandPerimeterInfo` | `true` | commands | Enables /perimeterinfo command that scans the area around the block for potential spawnable spots |
| `commandPing` | `true` | commands | Enables /ping for players to get their ping |
| `commandPlayer` | `true` | commands | Enables /player command to control/spawn players |
| `commandProfile` | `true` | commands | Enables /profile command to profile lag |
| `commandPublicScoreboard` | `true` | commands | Allows the use of scoreboardPublic, a lower permition level score display. |
| `commandRepopulate` | `false` | commands | Enables /repopulate command to repopulate given chunk |
| `commandRNG` | `true` | commands | Enables /rng command to manipulate and query rng |
| `commandSpawn` | `true` | commands | Enables /spawn command for spawn tracking |
| `commandStructure` | `true` | commands | Enables /structure to manage NBT structures used by structure blocks |
| `commandTick` | `true` | commands | Enables /tick command to control game speed |
| `commandTicktimes` | `true` | commands | Enables /ticktimes command to to log ticktimes |
| `commandUnload` | `true` | commands | Enables /unload command to inspect chunk unloading order |
| `commandVillage` | `true` | commands | Enables /village to query information about villages. |
| `commandWaypoint` | `true` | commands | Enables /waypoint for saving coordinates |
| `craftingWindowDuplication` | `false` | experimental | Adds back the crafting window duplication bug. |
| `creativeNoClip` | `false` | creative | Enable creative player no-clip. |
| `creativeOneHitKill` | `false` | creative, kisde | Creative players kill entities in one hit; sneaking also kills nearby entities. |
| `creativeOpenContainerForcibly` | `false` | creative, kisde | Creative players can open physically blocked containers. |
| `ctrlQCraftingFix` | `false` | fix, survival | Dropping entire stacks works also from on the crafting UI result slot |
| `customMOTD` | `_` | creative | Sets a different motd message on client trying to connect to the server |
| `desertShrubs` | `false` | feature | Saplings turn into dead shrubs in hot climates and no water access when it attempts to grow into a tree |
| `dimensionLoading` | `false` | feature | Removes the dimension loading where after 300 gameticks all entitys freeze without players. |
| `disableBedrockPlacement` | `false` | feature | Disables placement of the bedrock item |
| `disableBlockSelfChecks` | `false` | experimental, creative, vastech | Suppresses block added and break callbacks during block replacement |
| `disableBookBan` | `false` | fix | Disables the packet limit that causes the book banning. |
| `disableLightUpdates` | `false` | creative, vastech | Skips light propagation checks for technical testing |
| `disablePlayerCollision` | `false` | creative, experimental | Disables player entity collision. |
| `disableSpawnChunks` | `false` | creative | Removes the spawn chunks. |
| `disableUpdateSuppressionLogging` | `false` | fix, vastech | Logs update suppression tasks as one line instead of a stack trace |
| `disableVanillaTickWarp` | `false` | fix | Disables the catching-up behavior after lag spikes |
| `disableVillagerQueue` | `false` | creative | Disables the village door ticking queue. |
| `disableWandering` | `false` | creative | Disables random entity AI wandering |
| `dismountFix` | `false` | fix | Fix dismount behavior that leads to ghost chicken jockeys |
| `dispenserMinecartFiller` | `false` | experimental | Minecarts can be filled with hoppers, chests, tnt and furnace. |
| `dispenserWaterBottle` | `false` | experimental | Water bottles in dispensers fill with water when dispensed with water in front. |
| `displayMobAI` | `false` | creative | Uses nametags to display current mobs AI tasks |
| `doorCheckOptimization` | `false` | experimental, optimizations | Optimizes checking for wooden doors. |
| `doorDeregistrationTime` | `1200` | creative | Changes time before doors are deregistered. |
| `doorSearchOptimization` | `false` | experimental, optimizations | Optimizes the door search for iron farms. |
| `doubleRetraction` | `false` | experimental | 1.8 double retraction from pistons. |
| `doubleTileTickSchedulingFix` | `false` | fix | Fixes double tile tick scheduling |
| `duplicationFixGravityBlocks` | `false` | fix | Fixes duplication of items when using gravity blocks through portals |
| `duplicationFixItemFrame` | `false` | fix | Fixes duplication of items when using item frames |
| `duplicationFixLogout` | `false` | fix | Fixes duplication of items when players drop items on the ground and log out the same time |
| `duplicationFixMovingCarpets` | `false` | fix | Fixes duplication of carpets when pushed by pistons |
| `duplicationFixMovingRail` | `false` | fix | Fixes duplication of rails when pushed by pistons |
| `duplicationFixMovingTNT` | `false` | fix | Fixes duplication of TNT when pushed by pistons |
| `duplicationFixPortalEntitys` | `false` | fix | Fixes duplication of items when entitys enter end portals and die the same time |
| `duplicationFixRidingEntitys` | `false` | fix | Fixes duplication of entitys when players log out riding entitys in unloaded chunks |
| `duplicationFixUpdateSuppression` | `false` | fix | Fixes duplication of blocks when using update suppression |
| `effectsFix` | `false` | fix | Recovers potion effects when they were replaced and the replacement ended |
| `elytraCheckFix` | `false` | fix | Fixes the elytra check similar to 1.15 where the player do not have to fall to deploy elytra anymore. |
| `elytraTradable` | `false` | feature | Elytras can be traded by Leatherworker villagers. |
| `enableStableLCGNetherEnd` | `false` | creative | Disables snow, ice and lightning in nether and end for stable LCG. |
| `endGatewayCooldown` | `true` | survival, kisde | Toggle for end gateway cooldown |
| `endRNG` | `false` | creative | Turning end RNG manipulation on or off. |
| `entityDuplicationFix` | `false` | fix | Chunk saving issues that causes entites and blocks to duplicate or disappear |
| `entityTrackerFix` | `false` | fix | Fixes entity tracker not rendering entitys such as players in minecarts or boats. |
| `explosionNoBlockDamage` | `false` | tnt | Explosions won't destroy blocks |
| `extendedConnectivity` | `false` | experimental | Quasi Connectivity doesn't require block updates. |
| `extremeBehaviours` | `false` | creative, experimental, bullet | Edge cases are as frequent as common cases, for testing only!! |
| `fakePlayerFallDamage` | `true` | creative, kisde | Toggle whether fake players take fall damage |
| `fakePlayerStats` | `false` | feature | Allows fake players to gain stats. |
| `fallingMovementOptimization` | `false` | optimizations | Optimizes falling entity movement when using cannons. |
| `farmlandBug` | `false` | experimental | Adds back farmland bug where entities teleport on top of farmland that turns back to dirt. |
| `fastMovingEntityOptimization` | `false` | experimental | Optimized movement calculation or very fast moving entities |
| `fillLimit` | `32768` | creative | Customizable fill/clone volume limit |
| `fillUpdates` | `true` | creative | fill/clone/setblock and structure blocks cause block updates |
| `fixedPearlBugs` | `false` | fix | Fixes the pearl bugs removing them when players relog, similar fix to mc1.15. |
| `flippinCactus` | `false` | creative, survival | Players can flip and rotate blocks when holding cactus |
| `flyingMachineTransparent` | `false` | creative | Transparent observers, TNT and redstone blocks. May cause lighting artifacts |
| `freezeBlockEventsOnTickFreeze` | `true` | creative, vastech | Freezes queued block events during tick freeze |
| `freezeVillageChanges` | `false` | creative | Freezes the game when villages are modified. |
| `golemCap` | `true` | creative | Toggles golem cap for iron farms. |
| `golemCounter` | `false` | creative | Counts golem spawns. Enables /golem command. |
| `golemRNG` | `false` | creative | Use alternate rng to prevent rng manipulation of golem spawns. |
| `golemSwimFix` | `false` | creative | Stops iron golems from swimming in certain edge cases. |
| `growingUpWallJumpFix` | `false` | fix | Mobs growing up won't glitch into walls or go through fences |
| `hardcodeTNTangle` | `-1` | tnt | Sets the horizontal random angle on TNT for debugging of TNT contraptions |
| `hopperCounters` | `HopperCounters.off` | commands, creative, survival | Count items passing through hoppers |
| `hopperCountersUnlimitedSpeed` | `false` | creative, kisde | Wool counter hoppers transfer without normal cooldown. |
| `hopperNoItemCost` | `false` | creative, survival, kisde | Wool above a hopper lets it supply items without consuming them. |
| `huskSpawningInTemples` | `false` | experimental, feature | Only husks spawn in desert temples |
| `ignoreEntityWhenPlacing` | `false` | creative | Allows players to place blocks inside entity's. |
| `inconsistentRedstoneTorchesFix` | `false` | fix | Redstone torches respond correctly to 2 tick pulses |
| `instantDrinkPotion` | `false` | feature | Make potions instant drinkable. |
| `instantFallingFlag` | `false` | creative | Sets the instant falling flag to true. The boolean used in world population that can be exploited turning true making falling blocks fall instantly. |
| `instantScheduling` | `false` | creative | Sets the instant scheduled updates instantly to true. The boolean used in world population that can be exploited turning true making all repeaters, comperators, observers and similar components update instantly. |
| `invisibilityFix` | `false` | fix | Guardians, ghasts, blazes... honor players' invisibility effect |
| `ironFarmAABBOptimization` | `false` | experimental, optimizations | Optimizes AABB checks in iron farms. |
| `itemDesynchFix` | `false` | fix | Fixes item desynchs when transfering items. |
| `leashFix` | `LeashFix.off` | fix | Fixes to leashes. |
| `limitITTupdates` | `0` | fix | A limiter for updates happening on the main thread to prevent crashes on instant tile tick. |
| `liquidsNotRandom` | `false` | feature | Liquids don't ignore random tick updates |
| `llamaOverfeedingFix` | `false` | fix | Prevents llamas from taking player food while breeding |
| `maxEntityCollisions` | `0` | optimizations | Customizable maximal entity collision limits, 0 for no limits |
| `mergeTNT` | `false` | tnt | Merges stationary primed TNT entities |
| `miningGhostBlocksFix` | `false` | fix | Remove ghost blocks when mining too fast |
| `missingTools` | `false` | survival | Pistons, Glass and Sponge can be broken faster with their appropriate tools |
| `mobsDontControlMinecarts` | `false` | feature | Mobs no longer can control minecarts |
| `movableTileEntities` | `false` | experimental | Pistons can push tile entities, like hoppers, chests etc. |
| `netherRNG` | `false` | creative | Turning nether RNG manipulation on or off. |
| `newLight` | `false` | optimizations | Uses alternative lighting engine by PhiPros. AKA NewLight mod |
| `nitwitCrafter` | `false` | experimental | Nitwit villagers will have 3 hidden crafting recipes they can craft |
| `noteBlockImitationOf1.13` | `false` | experimental | Note blocks have update capabilities behaviour from 1.13 |
| `observerPoweredTerracotta` | `false` | experimental, creative | Observer delays depends on stained hardened clay aka terracotta on which they are placed |
| `observersDoNonUpdate` | `false` | creative | Observers don't pulse when placed |
| `opPlayerNoCheat` | `false` | commands, kisde | Blocks OP players from using gamemode, tp, teleport, give, setblock and summon |
| `optimizedCollisionCancellations` | `false` | fix | Fixes the collision cancelation lag when mobs are inside ladders and vines. |
| `optimizedDespawnRange` | `false` | optimizations | Spawned mobs that would otherwise despawn immediately, won't be placed in world |
| `optimizedTileEntities` | `false` | experimental | Reduces the lag caused by tile entities. |
| `optimizedTileEntityRemoval` | `false` | optimizations | Optimizes tile entity removal from the world. |
| `optimizedTNT` | `false` | tnt | TNT causes less lag when exploding in the same spot and in liquids |
| `oreUpdateSuppressor` | `false` | creative | Emerald ore receiving a block update will throw a StackOverflowError, simulating an update suppressor. |
| `pcaProtocol` | `false` | experimental, kisde | Allows OPs to preview nearby server container inventories with Tweakeroo |
| `persistentParrots` | `false` | survival, feature | Parrots don't get of your shoulders until you receive damage |
| `pistonGhostBlocksFix` | `PistonGhostBlocksFix.off` | fix | Fix for piston ghost blocks. 'clientAndServer' option requires carpet-client |
| `pistonSerializationFix` | `false` | fix | Fixes bug with piston serialization |
| `playerChunkLoadingFix` | `false` | fix | Fixes player position truncation causing chunks to load with one block offset to chunk boarders in negative coordinates. |
| `playerSit` | `false` | creative, kisde | Sit after three quick sneaks while on the ground. |
| `playerSkullsByChargedCreeper` | `false` | feature | Players drop there skulls when blown up by charged creepers. |
| `playersTurningInvisibleFix` | `false` | fix | Players go invisible when dying or using portals. |
| `pocketPushing` | `false` | experimental | Reintroduces piston warping/translocation bug |
| `portalCaching` | `false` | survival, experimental | Alternative caching strategy for nether portals |
| `portalCreativeDelay` | `false` | creative | Portals won't let a creative player go through instantly |
| `portalSuffocationFix` | `false` | fix | Nether portals correctly place entities going through |
| `portalTeleportationFix` | `false` | fix | Nether portals won't teleport you on occasion to 8x coordinates |
| `potionsDespawnFix` | `false` | fix | Allows mobs with potion effects to despawn outside of player range |
| `prometheusIntegration` | `false` | experimental, bullet | Prometheus integration |
| `publicKick` | `false` | commands | Reduces the permition level to kick players for everyone. |
| `pushLimit` | `12` | creative | Customizable piston push limit |
| `quasiConnectivity` | `true` | creative | Pistons, droppers and dispensers react if block above them is powered |
| `railPowerLimit` | `9` | creative | Customizable powered rail power range |
| `randomtickingChunkUpdates` | `false` | creative | Enables randomtick indexing on carpet client. |
| `randomTickOptimization` | `false` | fix | Stops blocks which don't need to be random ticked from being random ticked |
| `redstoneDustAlgorithm` | `RedstoneDustAlgorithm.vanilla` | experimental, optimizations | Redstone dust algorithm |
| `redstoneMultimeter` | `false` | creative, survival, commands | Enables integration with the new Redstone Multimeter mod |
| `redstoneMultimeterLegacy` | `false` | creative, survival | Enables integration with NarcolepticFrog's Redstone Multimeter mod |
| `redstoneOreRedirectsDust` | `false` | experimental, feature | Redstone ore blocks can redirect redstone dust |
| `relaxedBlockPlacement` | `false` | creative | Pumpkins and fence gates can be placed in mid air |
| `reloadEntitySpeedlossFix` | `false` | fix | Fixes the speed los on entitys after reload. |
| `reloadFakePlayers` | `false` | feature | Reloads fake players on server startup that were loaded before server shutdown. |
| `reloadSuffocationFix` | `false` | fix | Won't let mobs glitch into blocks when reloaded. |
| `reloadUpdateOrderFix` | `false` | fix | Fixes reload update order for tile entities |
| `removeConnectionTimeout` | `false` | fix | Removes the timeout caused by delays attempting to login. |
| `removeFakePlayerSkins` | `false` | feature | Removes the skin from fake players to reduce player data requests to mojang servers. |
| `removeTNTVelocity` | `false` | creative | Removes tnt applying velocity to other entities. |
| `renewableDragonEggs` | `false` | experimental | Dragon eggs when fed meet items spawn more eggs |
| `renewableElderGuardians` | `false` | experimental, feature | Guardians turn into Elder Guardian when struck by lightning |
| `renewablePackedIce` | `false` | experimental | Multiple ice crushed by falling anvils make packed ice |
| `repeaterPoweredTerracotta` | `false` | experimental, creative | Repeater delays depends on stained hardened clay aka terracotta on which they are placed |
| `rideableGhasts` | `false` | survival, feature | Named ghasts won't attack players and allow to be ridden and controlled |
| `ridingPlayerUpdateFix` | `false` | fix | Fixes chunk updates for players riding minecarts or llamas |
| `rotatorBlock` | `false` | experimental | Cactus in dispensers rotates blocks. |
| `saveSavestates` | `false` | fix | Enables best-effort saving of savestated chunks |
| `schematicPaste` | `false` | creative, experimental, kisde | Allows OPs in creative mode to paste blocks and block entities with Litematica |
| `schematicPasteNoUpdates` | `false` | creative, kisde | Suppresses block-added, neighbor, observer and inventory comparator updates caused by schematic paste |
| `schematicSave` | `false` | creative, experimental, kisde | Allows OPs in creative mode to capture loaded blocks and block entities with Litematica |
| `scoreboardDelta` | `0` | experimental | Scoreboard displays changes over time, specified in seconds. |
| `scoreboardStats` | `false` | experimental, survival, bullet | Enables /sb to display player's stats as scoreboard with a total entry |
| `sendDuplicateEntitiesToClients` | `false` | fix | Sends invisible duplicate UUID entities to clients |
| `setBlockCanPlaceLitObservers` | `false` | fix, creative, vastech | Preserves the powered metadata bit when placing observers with commands |
| `shulkerSpawningInEndCities` | `false` | feature, experimental | Shulkers will respawn in end cities |
| `signComparatorDisplay` | `false` | creative, vastech | Standing signs on emerald blocks display received redstone power |
| `signComparatorOutput` | `false` | creative, vastech | Signs attached to diamond blocks output the first line as a comparator signal |
| `silverFishDropGravel` | `false` | experimental | Silverfish drop a gravel item when breaking out of a block |
| `simulatePermaloader` | `false` | creative | When true, the game acts as if a permaloader is running |
| `skyblock` | `false` | feature | Enables skyblock on 1.12, all blocks but end portal frames will be removed in newly generated chunks. |
| `sleepingThreshold` | `100` | experimental | The percentage of required sleeping players to skip the night |
| `spectatorsDontLoadChunks` | `false` | creative, experimental | Spectators can no longer load chunks. WARNING! this is a highly experimental feature, use with care and with chunk debug to understand the chunkloading side effects. |
| `spongeRandom` | `false` | experimental, feature | sponge responds to random ticks |
| `stackableEmptyShulkerBoxes` | `false` | survival | Empty shulker boxes can stack to 64 when dropped on the ground |
| `stackableShulkersPlayerInventory` | `false` | feature | Allows empty shulkerboxes to stack in the player inventory. |
| `structureBlockLimit` | `32` | creative | Changes the structure block dimension limit. |
| `structuresReplaceEntities` | `false` | creative | Structure blocks remove entities in the bounding box when load entity option is enabled. |
| `summonNaturalLightning` | `false` | creative | summoning a lightning bolt has all the side effects of natural lightning |
| `tickingAreas` | `false` | creative, experimental | Enable use of ticking areas. |
| `tickVillages` | `true` | creative, vastech | Enables village and village siege ticking |
| `tileTickLimit` | `65536` | survival | Customizable tile tick limit |
| `TNTAdjustableRandomAngle` | `false` | tnt | Enables controlable TNT jump angle RNG for debuging. |
| `TNTdoesNotPrime` | `false` | creative, tnt, vastech | Prevents existing TNT from priming on redstone neighbor updates |
| `TNTDoNotUpdate` | `false` | tnt | TNT doesn't update when placed against a power source |
| `tntFuseLength` | `80` | creative | Changes default tnt fuse. |
| `TNTmovementOptimization` | `false` | optimizations | Optimizes TNT movement when using cannons. |
| `tntPrimerMomentumRemoved` | `false` | tnt | Removes random TNT momentum when primed |
| `tntRandomRange` | `-1` | tnt | Sets the tnt random explosion range to a fixed value |
| `totallyNoBlockUpdates` | `false` | experimental, creative, vastech | Suppresses block neighbor, observer and comparator notifications |
| `tpAcrossDimensions` | `true` | creative | /tp will teleport the players across dimensions |
| `unloadedEntityFix` | `false` | experimental, creative | Entities pushed or moved into unloaded chunks no longer disappear |
| `updateCarpetAll` | `false` | creative | Makes update carpet public for all users. |
| `updateSuppressionCrashFix` | `false` | fix | Fixes updates suppression causing server crashes. |
| `updateTabEveryGametick` | `false` | experimental, bullet | Tab info updates every gametick |
| `viewDistance` | `0` | creative | Changes the view distance of the server. |
| `villagerAutoTrader` | `false` | experimental | Villagers automaticaly trade from items on the ground |
| `villagerInventoryDropFix` | `false` | fix | Villagers drop there inventory contents when they die. |
| `villagerQueueLength` | `64` | creative | Sets the length of the villager queue. Default 64 for up to 65 positions in the queue |
| `villagersRandomStart` | `false` | creative | Delays the first door check of villagers instead of running instantly. |
| `villagerTickingRate` | `0` | creative | Changes villager ticking rate. Set 0 for default 70-119 rate |
| `watchdogFix` | `false` | fix | Fixes server crashing under heavy load and low tps |
| `waterFlow` | `WaterFlow.vanilla` | optimizations | fixes water flowing issues |
| `whereToChunkSavestate` | `WhereToChunkSavestate.unload` | creative | Where chunk savestating is allowed to happen |
| `wirelessRedstone` | `false` | creative | Repeater pointing from and to wool blocks transfer signals wirelessly |
| `worldEdit` | `false` | creative, experimental | Enables/disables WorldEdit. |
| `xpNoCooldown` | `false` | creative | Players absorb XP instantly, without delay |

</details>

## Documentation

- [Server integrations](docs/usage/server-integrations.rst)
- [Carpet installation](docs/usage/install.rst)
- [Carpet manual](docs/index.rst)

## Building from source

Run `./gradlew setupCarpetmod` to generate the source tree, then make source changes and run `./gradlew createRelease`. This task creates an obfuscated patch archive under `build/distributions/`. The generated `src/` tree can be overwritten by setup tasks; preserve local edits before syncing.

The separate companion client project targets LiteLoader and Java 8. Its build is `./gradlew build --offline` when pinned dependencies are already cached.

See [Vastech technical tools](docs/usage/vastech-tools.rst) for source categories, cluster commands and imported testing rules.

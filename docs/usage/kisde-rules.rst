Kisde-Carpet rules
==================

endGatewayCooldown
------------------

Default: ``true``. Set to ``false`` to bypass End Gateway teleport cooldown.
Restoring ``true`` restores the remaining cooldown. Gateway spawning and
teleport destination selection are unchanged.

fakePlayerFallDamage
--------------------

Default: ``true``. When ``false``, Carpet fake players ignore fall damage.
Real player falls and fake-player damage from other sources are unchanged.

creativeOneHitKill
------------------

Default: ``false``. Creative attacks kill the target immediately, including dragon parts. Sneaking also kills attackable entities within the target box expanded by 2 blocks horizontally and 0.5 vertically. Creative and spectator players are protected; nearby hanging entities and armor stands are excluded from the sweep.

blockPlacementIgnoreEntity
--------------------------

Default: ``false``. Creative block placements bypass entity collision checks. Replaceability, block support and player editing permissions still apply. Survival placements retain vanilla collision checks.

creativeOpenContainerForcibly
-----------------------------

Default: ``false``. Creative players can open chests, Ender Chests and shulker boxes despite physical lid obstructions. This includes both halves of a double chest. Locks and container permissions still apply; survival players and hopper inventory access keep their normal checks.

playerSit
---------

Default: ``false``. Three start-sneak transitions, each less than 300 ms apart while on the ground, create an invisible seat. Release sneak between presses; another sneak dismounts. Seats disappear after dismount, death, disconnect, dimension change or disabling the rule. Seats are transient and never saved to the world; ordinary armor stands are unaffected.

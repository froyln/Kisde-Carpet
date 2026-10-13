Essential-style rules
=====================

These additions adapt selected behaviors from `EssentialAddons
<https://github.com/Super-Santa/EssentialAddons>`_ (revision
``5d2d5b6eff05561e92e9d8730cc9564dfe3fe802``; see its
`MIT license <../licenses/EssentialAddons.txt>`_).
All new rules default to ``false``. They run on the server without the client
companion.

Careful pickup
--------------

``carefulBreak`` is the existing Carpet rule; its behavior is unchanged.
``carefulDrop`` sends entity drops directly to the killer's inventory.
An administrator enables the rules, then each player opts in::

    /carpet setDefault carefulBreak true
    /carpet setDefault carefulDrop true
    /subscribe carefulBreak
    /subscribe carefulDrop

Sneak while mining or killing. Run the same subscription command again to
unsubscribe. ``/suscribe`` is an alias for ``/subscribe``; ``/log`` also works.
Subscriptions alone do not enable a disabled server rule.

Careful drop covers mob loot and equipment, player death inventories, armor
stands, boats, minecarts and their contents, item frames and paintings. It uses
the actual damage source, including player-fired projectiles. Existing loot,
creative-mode and gamerule conditions still decide whether items drop. XP orbs
are unchanged. Items that do not fit still drop normally, preserving their NBT.
Careful break retains its existing mining and pickup behavior.

Command shortcuts
-----------------

``commandGM``, ``commandHat`` and ``commandMore`` accept ``false`` (disabled),
``true`` (everyone), ``ops`` (permission level 2), or ``0`` through ``4``.
For example::

    /carpet setDefault commandGM ops
    /carpet setDefault commandHat true
    /carpet setDefault commandMore ops

* ``commandGM`` enables ``/gmc`` (creative), ``/gms`` (survival), ``/gma``
  (adventure) and ``/gmsp`` (spectator). Each changes only the sender's gamemode.
  The existing ``/c`` and ``/s`` camera commands are separate. Operator players
  remain blocked from the new shortcuts while ``opPlayerNoCheat`` is enabled.
* ``commandHat`` enables ``/hat``. One main-hand item becomes your hat. In
  survival, the old hat returns to your inventory or drops if it does not fit.
  An empty hand removes the hat. Creative players copy the held item onto their
  head without consuming it or returning the old hat, matching EssentialAddons.
* ``commandMore`` enables ``/more``. It fills the main-hand item to its normal
  maximum stack size, using the offhand if the main hand is empty. Item NBT and
  durability are retained. It creates items, so choose its permission level
  accordingly.

``commandPublicOp`` is a separate boolean rule. When enabled, **anyone can use
/op to grant operator access**, including to themselves. Disabling it restores
normal command permissions; it does not remove operators already granted.

Enchantments and anvils
-----------------------

``disableEnchantmentCap`` permits above-normal levels through ``/enchant`` and
anvil combining. Equal levels combine into the next level. Item applicability
and incompatible-enchantment checks remain in place. Minecraft 1.12.2 stores
levels in signed-short NBT, so the highest supported level is 32,767.
Enchantment tables and loot generation retain their normal levels.

``disableAnvilXpLimit`` allows survival anvil operations costing 40 or more
levels. It still requires and charges the displayed operation's full level
cost; creative players remain exempt. The normal rename-only cost cap remains.
Prior-work costs saturate at the positive integer limit instead of overflowing.
A vanilla client may still show the red “Too Expensive!” label for costs of
40 or more; the server supplies the output and validates whether it can be
picked up.

Fake-player inventories
-----------------------

``fakePlayerDropInventoryOnKill`` drops a fake player's inventory when removed
with ``/player <name> kill`` or ``/kill <name>``, even with ``keepInventory``
enabled. Vanishing-cursed items are destroyed before dropping. With the rule
false, those commands retain their existing logout behavior.

Ordinary deaths keep their normal gamerule behavior. Login displacement,
shadow-player despawn and server shutdown do not gain inventory drops.

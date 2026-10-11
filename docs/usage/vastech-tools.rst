Vastech technical tools
======================

These server tools are adapted from `Carpet Vastech Addition
<https://github.com/Void-Skeleton/Carpet-Vastech-Addition>`_, revision
``d0ac9fd22949e903d5353103f134115dcaba54fa`` (LGPL-3.0).
They require no companion client.

Rule categories
---------------

``/carpet list bullet`` lists inherited Bullet Carpet additions.
``/carpet list kisde`` lists the integrations and rules added in Kisde-Carpet.
``/carpet list vastech`` lists these Vastech imports.
The same categories appear in the Carpet menu and tab completion. Rules also
retain their functional categories, such as ``creative`` and ``fix``.

Cluster tools
-------------

Enable ``/carpet commandCluster true``. All ``/cluster`` operations require
command permission level 2. Parameters and calculated results are separate for
each command sender. One calculation runs at a time, off the server thread.

* ``/cluster peek`` prints the current parameter NBT.
* ``/cluster read <NBT>`` updates parameters and clears the previous result.
* ``/cluster compute`` finds a target, cluster chunks and a connecting loading grid.
* ``/cluster loadCluster`` loads the calculated cluster chunks in the sender's dimension.
* ``/cluster loadGrid`` loads the connecting grid in that dimension.
* ``/cluster constructLoader`` reports disconnected grid chunks without placing blocks.
* ``/cluster constructLoader <block> <metadata> [y]`` builds hopper/chest bridges.
  The default height is 64; allowed heights are 0–254.

For example::

    /cluster read {startX:0,startZ:0,endX:1,endZ:1,hashSize:8192,desiredClustering:100,clusterWidth:90,maxClusterHeight:200,clusterCornerX:100,clusterCornerZ:100,widthDir:"east",heightDir:"south"}
    /cluster compute
    /cluster constructLoader

Coordinates are chunk coordinates. Search end coordinates are exclusive.
``hashSize`` must be a power of two from 2 to 65,536; it should match the
chunk map capacity being investigated. Width and height directions must be
perpendicular and horizontal. Invalid settings are rejected without replacing
the current parameters. Calculation limits are 65,536 candidate cluster
chunks, 4,194,304 hash/height cells, 4,096 target chunks, 16,777,216
candidate/target combinations and 134,217,728 hash/height/target combinations.
A calculation with no solution clears its prior result.

Loading and actual construction require creative mode for players; the console
can perform them. They run on the server thread and are limited to 4,096 grid
chunks within the world's border. Loading can generate chunks and does not
promise permanent chunk tickets. Construction requires a loaded, empty area
covering each grid chunk's central cross and the blocks above its center. Use
``loadGrid`` first if needed. The bridge block must be solid and have no block
entity. Construction supplies shears in hoppers and has no automatic undo.
The calculator models chunk hashing; it does not automatically detect map
capacity or prove that a loader will survive every server unloading pattern.

Technical rules
---------------

All rules below are in ``vastech``. Only ``tickVillages`` and
``freezeBlockEventsOnTickFreeze`` default to true, preserving existing behavior.

* ``tickVillages`` controls village and village siege ticking.
* ``freezeBlockEventsOnTickFreeze`` retains queued block events while frozen.
  False lets those events run during freeze.
* ``disableLightUpdates`` skips light propagation checks. Re-enabling does not
  automatically repair lighting skipped while active.
* ``TNTdoesNotPrime`` prevents existing TNT from priming on redstone neighbor
  updates. Placement priming still uses ``TNTDoNotUpdate``; fire and explosions
  keep their existing behavior.
* ``setBlockCanPlaceLitObservers`` preserves observer metadata bit 8, allowing
  commands to place powered observers.
* ``disableUpdateSuppressionLogging`` replaces suppressed-task stack traces
  with one line. Unrelated task exceptions retain their normal logging.
* ``castSuppressorCrashFix`` converts class cast errors to suppression exceptions
  inside the existing ``updateSuppressionCrashFix`` guarded neighbor/observer
  paths. It does not repair mismatched block entities.
* ``totallyNoBlockUpdates`` suppresses neighbor, observer and comparator
  notifications through the normal world notification methods.
* ``disableBlockSelfChecks`` skips block-added and break-block callbacks during
  replacement. These two global suppression rules are for technical tests;
  normal support checks, inventory drops and redstone reactions may be skipped.
* ``signComparatorOutput`` lets a sign attached to a diamond block output the
  number on its first line. Decimal, ``0b`` binary, ``0x`` hexadecimal and leading
  zero octal are supported; output is clamped to 0–15 and invalid input gives 0.
* ``signComparatorDisplay`` writes received redstone power onto standing signs
  on emerald blocks when they receive neighbor updates. The first two lines
  show decimal and hexadecimal power, replacing their previous text.

Existing Bullet ``blockStateSyncing`` and Kisde schematic integrations are
reused rather than registering duplicate implementations.

Shared schematics
=================

Kisde-Carpet can keep a server library of Litematica schematic files and their
placements. Players with the Kisde-Carpet LiteLoader companion can browse the
library from Litematica's main menu, download a schematic and load it as a
local overlay. A shared placement includes its dimension, origin, rotation,
mirror and subregion settings. Loading an overlay does not change blocks in the
world.

Enable sharing
--------------

An operator enables the ``schematicShare`` Carpet rule (disabled by default)::

   /carpet schematicShare true

Any connected player can upload a new share while the rule is enabled. The
author and operators can update, rename or remove a share. A player must be in the
placement's dimension to load it. Updates are explicit: select a Litematica
placement and use the row’s **Update** button in the browser. Clients that already loaded
that share refresh its overlay when the update arrives.

For players
-----------

Install the companion client mod and connect to a server with sharing enabled.
Open Litematica's main menu and choose **Shared schematics**, beside
**Configuration Menu**. Each shared schematic has **Load**, **Update**,
**Rename** and **Remove** buttons in its row. Management buttons are enabled
for its author and operators; **Update** replaces the share with the currently
selected Litematica placement. **Rename** changes only the shared catalog name
and keeps the file, placement and author intact.

To upload, use **Share** on a placement’s row in **Schematic Placements List**.
Its schematic data and placement settings are sent to the server.
The search field filters the full catalog locally by schematic name or author.
Search and scrolling also work with earlier sharing servers.
Use the mouse wheel or drag the scrollbar to browse larger catalogs. The list
shows only rows that fit above the bottom controls. Use **Refresh** to reload
the catalog.

Storage and limits
------------------

The library is stored in the server world's ``kisde-shared-schematics``
directory, so it persists with that world. Each file is limited to 16 MiB
compressed and 64 MiB expanded. A file can contain at most 32 regions and 16
million blocks total. The catalog allows 256 shares, at most 32 per author,
with a 1 GiB logical storage limit. Transfers use bounded chunks and incomplete
uploads are removed after a timeout or server restart.

Only the server stores and distributes these files; the client caches verified
downloads by server identity, share and content hash. The sharing protocol is
Kisde-Carpet's own and is not wire-compatible with modern Syncmatica. Syncmatica
is the design reference for sharing schematic files and placements:
`https://github.com/sakura-ryoko/syncmatica`_.

.. _`https://github.com/sakura-ryoko/syncmatica`: https://github.com/sakura-ryoko/syncmatica

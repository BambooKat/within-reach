# Within Reach

A Fabric mod for Minecraft that puts your nearby storage one click away.

Open your inventory, a crafting table or a furnace and a side panel lists everything stored in the chests, barrels, shulker boxes and ender chest around you. No more running back to the storage room.

## Features

- **Take items from the panel**: left click takes a stack, right click takes one. The item ends up in your hand, or in your inventory with Shift.
- **Put items back**: drop what you carry on the panel and it returns to the container it came from (or any other one nearby).
- **Browse and search**: creative-style category tabs, a search box and pages.
- **Hover for details**: the tooltip shows the total and which containers hold the item, with the name you gave them (rename a chest in an anvil before placing it).
- **Furnaces too**: only their finished output is shown, so you never grab the coal you are cooking with.
- **Works with other mods' storage** through the `withinreach:storage` block tag.
- **Keyboard shortcut** (default `G`, changeable in Controls) and a button next to the inventory.
- English and Italian.

## Configuration

The reach radius (1-64 blocks, default 16) is set in `config/withinreach.json`, or from Mod Menu if you have it installed. On a server, the server's setting applies.

## Requirements

Minecraft 26.2, Fabric Loader, Fabric API. Mod Menu is optional.
For multiplayer, install the mod on both the server and the clients.

## License

MIT, see [LICENSE](LICENSE).

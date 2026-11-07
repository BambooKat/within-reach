# Within Reach: pagina Modrinth (testi da copiare)

## Impostazioni del progetto

- **Name**: Within Reach
- **URL slug**: within-reach
- **Summary** (max 256 caratteri):
  Browse and grab items from nearby chests, barrels, shulker boxes and your ender chest, right from your inventory, crafting table or furnace screen. No more trips to the storage room.
- **Categories**: Utility, Storage
- **Loader**: Fabric
- **Game version**: 26.2
- **Client / server**: Client: required, Server: required (per il multiplayer; in singleplayer funziona da solo)
- **License**: MIT
- **Icona**: design/icon_512.png
- **Dipendenze** (nella versione): Fabric API (required), Mod Menu (optional)

## Descrizione (incolla in "Description", formato Markdown)

**Within Reach** puts your nearby storage one click away.

Open your inventory, a crafting table or a furnace and a side panel lists everything stored in the chests, barrels, shulker boxes and ender chest around you. Take what you need without running back to the storage room.

## Features

- **Take items from the panel.** Left click takes a stack, right click takes one. The item goes to your hand, or straight to your inventory with Shift.
- **Put items back.** Drop what you carry on the panel and it returns to the container it came from, or to any other nearby one.
- **Browse and search.** Creative-style category tabs, a search box and pages.
- **See where things are.** The tooltip shows the total and which containers hold the item, using the name you gave them. Rename a chest in an anvil before placing it and it shows up by name.
- **Furnaces too.** Only finished output is listed, so you never grab the coal you are cooking with. Hoppers, dispensers, droppers and crafting stations are left out on purpose.
- **Fits any screen.** The panel moves the window out of the way when it needs room, and hides itself if there is really no space.
- **Shortcut and button.** Press `G` (rebindable in Controls) or click the chest button next to the inventory.
- **Configurable reach.** 1 to 64 blocks (default 16), from Mod Menu or `config/withinreach.json`.
- Available in English and Italian.

## Good to know

- On a server, install the mod on the server and on every client. The server's reach setting applies.
- Locked containers, trapped chests and unopened loot chests are never listed.
- Other mods' containers can be added through the block tag `withinreach:storage`.
- Requires Fabric API. Mod Menu is optional.

## Changelog 1.0.0

Initial release.

## Screenshot da caricare (consigliati)

1. Inventario con pannello aperto (GUI 3), con un oggetto in hover per mostrare il tooltip con i contenitori.
2. Banco di lavoro con il pannello accanto.
3. Forno: solo risultati nel pannello.
4. Schermata del raggio in Mod Menu.

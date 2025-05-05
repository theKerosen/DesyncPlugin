# Desync - A Minecraft Paranoia Plugin

[![Build Status](https://img.shields.io/badge/Status-In%20Development-orange)](https://github.com/LadyLuh/Desync)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Desync is a Minecraft plugin designed to subtly manipulate a player's clientside perception of the world, inducing a sense of paranoia and unease without directly disrupting core gameplay mechanics. Inspired by concepts seen in games and mods like "Broken Script", it introduces small, clientside visual, auditory, and environmental glitches that make the player question what is real.

The plugin leverages ProtocolLib to send custom packets to players, creating effects that only they can perceive, leaving the server and other players unaffected.

## Features

Desync introduces a variety of subtle, paranoia-inducing effects:

*   **Auditory Glitches:**
    *   **Footsteps:** Phantom footstep sounds heard nearby (random or mimicking player).
    *   **Misplaced Sound:** Distant, eerie ambient or mob sounds that don't fit the current environment.
    *   **Ghost Block Interaction:** Sounds of nearby blocks being interacted with (doors opening/closing, chests opening, levers clicking).
    *   **Fake Window Break:** The sound of glass breaking nearby.
*   **Visual Glitches:**
    *   **Peripheral Particles:** Strange particles briefly appearing in the player's peripheral vision.
    *   **Block Flicker:** A nearby block briefly changes to a different, similar material or turns off if it's a light source.
    *   **Visual Interact:** A nearby interactive block (like a door or trapdoor) briefly appears to open or close.
    *   **Block Vanish:** A single solid block briefly disappears clientside and then reappears.
    *   **Fake Block Crack:** A nearby block briefly displays a mining/cracking animation.
    *   **Fake Item Durability:** An item in the player's inventory or hotbar briefly shows reduced durability.
*   **Entity Perception:**
    *   **Animal Stare:** Nearby passive animals briefly turn their heads to stare intently at the player. Stops if the player looks back.
    *   **Animal Follow:** Nearby passive animals appear to glide towards the player when the player is not looking at them. Stops when looked at or after a duration. (Note: This is a clientside visual effect, animals don't actually pathfind).
    *   **Distant Stalker:** A fake player entity briefly appears far away, facing the player, and despawns if looked at.
    *   **The Glimpse:** An entity that shows up behind your back, it doesn't like to be stared at.
*   **UI Glitches:**
    *   **Action Bar Message:** A strange, short message appears briefly above the player's hotbar.
    *   **Fake Advancement:** A fake achievement/advancement pop-up appears briefly.
    *   **Fake Player Join:** A fake player named "Null" briefly appears in the player list (Tab menu). (Note: This does NOT send a chat message).
    *   **Inventory Shift:** Items in the player's inventory/hotbar briefly appear to shuffle clientside.

Events trigger randomly based on configurable chance and environmental factors like darkness, isolation, and time of day. Cooldowns prevent events from triggering too frequently for the same player or the same event type.

## Requirements

*   A Minecraft server running Spigot, Paper, or a compatible fork (Tested on Paper 1.21).
*   [**ProtocolLib**](https://www.spigotmc.org/resources/protocollib.1997/) (Required for packet manipulation). Ensure you are using a version compatible with your server version.

## Installation

1.  Download the latest `Desync-X.Y.Z.jar` from the [releases page](https://github.com/theKerosen/DesyncPlugin/releases).
2.  Download the compatible `ProtocolLib-X.Y.Z.jar` for your server version from the [ProtocolLib releases page](https://ci.dmulloy2.net/job/ProtocolLib/).
3.  Place both `.jar` files in your server's `plugins/` directory.
4.  Restart your Minecraft server.

## Configuration (W.I.P)

Desync uses a `config.yml` file located in the plugin's data folder (`plugins/Desync/`). This file allows you to customize various aspects of the plugin's behavior.

After editing the `config.yml`, you can apply the changes without a server restart by using the command `/ds reload`.

Key sections in the `config.yml`:

*   **`scheduler`**: Controls the frequency of the main event check and the base chance for an event to occur.
    *   `interval-ticks`: How often (in ticks) the plugin checks players.
    *   `base-event-probability`: The base chance (0.0 to 1.0) per player per check.
*   **`chances.multipliers`**: Defines how environmental factors (darkness, depth, isolation, time of day, dimension) multiply the `base-event-probability`.
*   **`chances.max-calculated-chance`**: Sets an upper limit (0.0 to 1.0) on the probability after multipliers are applied.
*   **`cooldowns`**: Sets the default cooldown durations (in milliseconds) for individual event types and the global cooldown applied after any event.
    *   `default-event-ms`: Default cooldown for a specific event type.
    *   `default-global-ms`: Cooldown applied after *any* event.
*   **`events`**: This section allows overriding the default cooldown for specific event types and configuring event-specific parameters (like messages for `fake_chat`).
    *   Example: `events.footstep.cooldown-ms: 45000` overrides the default cooldown for the `footstep` event.
    *   Example: `events.fake_chat.messages` is a list of strings used by the `fake_chat` event.

Refer to the comments within the generated `config.yml` for detailed explanations and examples.

## Commands

All administrative commands start with `/desync` or `/ds`. Permissions are required to use these commands.

*   `/ds trigger <EventType> [Player] [force]`
    *   Description: Manually triggers a specific desync event for yourself or another player.
    *   `<EventType>`: The key of the event to trigger (e.g., `footstep`, `stalker`, `fake_chat`). Use `/ds listevents` to see available keys.
    *   `[Player]`: Optional. The player to trigger the event for. Defaults to the command sender if omitted (requires console to specify a player).
    *   `[force]`: Optional. If included, bypasses cooldown and `canTrigger` checks.
    *   Permission: `desync.command.trigger`
*   `/ds cooldown clear <Player> [EventKey]`
    *   Description: Clears cooldowns for a specific player.
    *   `<Player>`: The player whose cooldowns to clear.
    *   `[EventKey]`: Optional. The key of a specific event type to clear the cooldown for. If omitted, clears ALL cooldowns (event and global) for the player. Use `/ds listevents` for keys.
    *   Permission: `desync.command.cooldown`
*   `/ds listevents` or `/ds events`
    *   Description: Lists all registered desync event types and their brief descriptions.
    *   Permission: `desync.command.listevents` (falls back to `desync.command`)
*   `/ds info`
    *   Description: Displays basic information about the plugin and its status.
    *   Permission: `desync.command.info` (falls back to `desync.command`)
*   `/ds reload`
    *   Description: Reloads the plugin's `config.yml` file.
    *   Permission: `desync.command.reload`

## Permissions

*   `desync.command`: Grants access to the base `/ds` command and basic subcommands (`info`, `listevents`).
*   `desync.command.trigger`: Grants access to the `/ds trigger` subcommand.
*   `desync.command.cooldown`: Grants access to the `/ds cooldown` subcommand.
*   `desync.command.listevents`: Grants access to the `/ds listevents` subcommand. (Inherits from `desync.command` by default).
*   `desync.command.info`: Grants access to the `/ds info` subcommand. (Inherits from `desync.command` by default).

By default, operators (`op`) have all permissions. You can manage permissions using a plugin like LuckPerms.

## Contributing

Contributions are welcome! If you have ideas for new subtle desync events, improvements, or bug fixes, feel free to open an issue or submit a pull request on the [GitHub repository](https://github.com/LadyLuh/Desync).

When adding new events, please aim for effects that:
*   Are primarily clientside (using ProtocolLib).
*   Do not disrupt the player's ability to interact with the world (mining, building, fighting).
*   Are subtle rather than jump-scare heavy.
*   Are creepy or unsettling, rather than just annoying.

## Credits

*   Inspired by the "Broken Script", "Herobrine Mod", "Herobrine AI" and similar paranoia-inducing concepts
*   Uses [ProtocolLib](https://www.spigotmc.org/resources/protocollib.39/) by dmulloy2.
*   Developed by LadyLuh.

## License

This project is licensed under the MIT License.

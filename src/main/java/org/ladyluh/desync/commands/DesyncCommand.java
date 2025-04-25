package org.ladyluh.desync.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.EventService;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.ladyluh.desync.managers.CooldownManager;

import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;


public class DesyncCommand implements CommandExecutor {

    private final Desync plugin;
    private final EventService eventService;
    private final CooldownManager cooldownManager;

    public DesyncCommand(@NotNull Desync plugin) {
        this.plugin = plugin;
        this.eventService = plugin.getEventService();
        this.cooldownManager = plugin.getCooldownManager();
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {


        if (!sender.hasPermission("desync.command")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use Desync commands.");
            return true;
        }


        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }


        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "trigger":
                handleTriggerCommand(sender, args);
                break;
            case "cooldown":
                handleCooldownCommand(sender, args);
                break;
            case "listevents":
            case "events":
                handleListEventsCommand(sender);
                break;
            case "info":
                handleInfoCommand(sender);
                break;
            case "reload":
                handleReloadCommand(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + args[0]);
                sendUsage(sender);
                break;
        }
        return true;
    }


    private void handleTriggerCommand(@NotNull CommandSender sender, @NotNull String[] args) {

        if (!sender.hasPermission("desync.command.trigger")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to trigger events.");
            return;
        }


        if (args.length < 2) {
            sendTriggerUsage(sender);
            return;
        }

        String eventTypeKey = args[1].toLowerCase();
        Player targetPlayer;
        boolean force = false;


        if (eventService.getEventByKey(eventTypeKey) == null) {
            sender.sendMessage(ChatColor.RED + "Unknown event type: '" + eventTypeKey + "'.");
            sendTriggerUsage(sender);
            return;
        }


        if (args.length >= 3) {
            String arg2 = args[2];
            if (arg2.equalsIgnoreCase("force") && args.length == 3) {

                force = true;
                if (sender instanceof Player) {
                    targetPlayer = (Player) sender;
                } else {
                    sender.sendMessage(ChatColor.RED + "'force' must be used with a player name when running from console.");
                    sendTriggerUsage(sender);
                    return;
                }
            } else {

                targetPlayer = Bukkit.getPlayerExact(arg2);
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player '" + arg2 + "' not found or not online.");
                    return;
                }
                if (args.length >= 4 && args[3].equalsIgnoreCase("force")) {
                    force = true;
                } else if (args.length > 4) {

                    sender.sendMessage(ChatColor.RED + "Too many arguments.");
                    sendTriggerUsage(sender);
                    return;
                }
            }
        } else {

            if (sender instanceof Player) {
                targetPlayer = (Player) sender;
            } else {
                sender.sendMessage(ChatColor.RED + "Please specify a player name when running trigger from console.");
                sendTriggerUsage(sender);
                return;
            }
        }


        sender.sendMessage(ChatColor.YELLOW + "Attempting to trigger event '" + eventTypeKey + "' for " + targetPlayer.getName() + (force ? " (forced)" : "") + "...");
        boolean eventTriggered = eventService.triggerEvent(targetPlayer, eventTypeKey, force);

        if (eventTriggered) {

            sender.sendMessage(ChatColor.GREEN + "Event '" + eventTypeKey + "' triggered successfully for " + targetPlayer.getName() + ".");
        } else {


            sender.sendMessage(ChatColor.RED + "Event '" + eventTypeKey + "' could not be triggered for " + targetPlayer.getName() + " (check console for details).");

        }
    }


    private void handleCooldownCommand(@NotNull CommandSender sender, @NotNull String[] args) {

        if (!sender.hasPermission("desync.command.cooldown")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to manage cooldowns.");
            return;
        }


        if (args.length < 3 || !args[1].equalsIgnoreCase("clear")) {
            sendCooldownUsage(sender);
            return;
        }

        Player targetPlayer = Bukkit.getPlayerExact(args[2]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[2] + "' not found or not online.");
            return;
        }

        UUID targetUUID = targetPlayer.getUniqueId();

        if (args.length >= 4) {

            String eventKey = args[3].toLowerCase();

            if (!eventService.getTriggerableEventKeys().contains(eventKey)) {
                sender.sendMessage(ChatColor.RED + "Unknown event key for cooldown: '" + eventKey + "'.");

                return;
            }
            cooldownManager.clearEventCooldown(targetUUID, eventKey);
            sender.sendMessage(ChatColor.GREEN + "Cleared event cooldown '" + eventKey + "' for " + targetPlayer.getName());
        } else {

            cooldownManager.clearCooldowns(targetUUID);
            sender.sendMessage(ChatColor.GREEN + "Cleared ALL cooldowns for " + targetPlayer.getName() + ".");
        }
    }


    private void handleListEventsCommand(@NotNull CommandSender sender) {

        if (!sender.hasPermission("desync.command.listevents") && !sender.hasPermission("desync.command")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to list events.");
            return;
        }

        Collection<PlayerDesyncEvent> events = eventService.getAllEvents();

        if (events.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No Desync events are currently registered.");
        } else {
            sender.sendMessage(ChatColor.GOLD + "--- Available Desync Events (" + events.size() + ") ---");

            events.stream()
                    .sorted(Comparator.comparing(PlayerDesyncEvent::getKey))
                    .forEach(event -> sender.sendMessage(ChatColor.YELLOW + " - " + event.getKey() + ChatColor.GRAY + ": " + event.getDescription()));
            sender.sendMessage(ChatColor.GOLD + "-----------------------------");
        }
    }


    private void handleInfoCommand(@NotNull CommandSender sender) {
        if (!sender.hasPermission("desync.command.info") && !sender.hasPermission("desync.command")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to see plugin info.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "--- Desync Plugin Info ---");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + plugin.getDescription().getVersion());

        if (!plugin.getDescription().getAuthors().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Author(s): " + String.join(", ", plugin.getDescription().getAuthors()));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Author(s): Unknown");
        }

        sender.sendMessage(ChatColor.YELLOW + "ProtocolLib: " + (plugin.getProtocolManager() != null ? ChatColor.GREEN + "Hooked" : ChatColor.RED + "Not Hooked"));
        sender.sendMessage(ChatColor.YELLOW + "Registered Events: " + eventService.getAllEvents().size());
        sender.sendMessage(ChatColor.GOLD + "--------------------------");
    }


    private void handleReloadCommand(@NotNull CommandSender sender) {
        if (!sender.hasPermission("desync.command.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reload the configuration.");
            return;
        }

        plugin.getConfigurationManager().reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Desync configuration reloaded.");
    }


    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Desync Commands ---");
        sender.sendMessage(ChatColor.YELLOW + "/ds trigger <EventType> [Player] [force]" + ChatColor.GRAY + " - Trigger an event.");
        sender.sendMessage(ChatColor.YELLOW + "/ds cooldown clear <Player> [EventKey]" + ChatColor.GRAY + " - Clear cooldowns.");
        sender.sendMessage(ChatColor.YELLOW + "/ds listevents" + ChatColor.GRAY + " - List available event types.");
        sender.sendMessage(ChatColor.YELLOW + "/ds info" + ChatColor.GRAY + " - Show plugin info.");
        sender.sendMessage(ChatColor.YELLOW + "/ds reload" + ChatColor.GRAY + " - Reload the config.");
        sender.sendMessage(ChatColor.GOLD + "-----------------------");
    }

    private void sendTriggerUsage(@NotNull CommandSender sender) {

        String eventTypes = eventService.getTriggerableEventKeys().stream()
                .sorted()
                .collect(Collectors.joining(", "));

        sender.sendMessage(ChatColor.YELLOW + "/ds trigger <EventType> [Player] [force]");
        sender.sendMessage(ChatColor.GRAY + "  Triggers a specific event for yourself or another player.");
        sender.sendMessage(ChatColor.GRAY + "  Use 'force' to bypass cooldowns and conditions.");
        sender.sendMessage(ChatColor.GRAY + "  Available Event Types: " + eventTypes);
    }

    private void sendCooldownUsage(@NotNull CommandSender sender) {
        String knownCooldownKeys = eventService.getTriggerableEventKeys().stream()
                .sorted()
                .collect(Collectors.joining(", "));
        sender.sendMessage(ChatColor.YELLOW + "/ds cooldown clear <Player> [EventKey]");
        sender.sendMessage(ChatColor.GRAY + "  Clears cooldowns for a player.");
        sender.sendMessage(ChatColor.GRAY + "  Omit EventKey to clear all cooldowns.");
        sender.sendMessage(ChatColor.GRAY + "  Known Event Keys: " + knownCooldownKeys);
    }
}
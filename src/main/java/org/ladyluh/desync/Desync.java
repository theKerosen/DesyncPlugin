package org.ladyluh.desync;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.ladyluh.desync.commands.DesyncCommand;
import org.ladyluh.desync.events.EventService;
import org.ladyluh.desync.listeners.PlayerQuitListener;
import org.ladyluh.desync.managers.CooldownManager;
import org.ladyluh.desync.scheduling.EventScheduler;
import org.ladyluh.desync.utils.SkinUtils;
import org.slf4j.Logger;

import java.util.Objects;


public final class Desync extends JavaPlugin {

    private static Desync instance;
    private final Logger logger;
    private ProtocolManager protocolManager;
    private EventScheduler eventScheduler;
    private CooldownManager cooldownManager;
    private EventService eventService;

    public Desync() {
        this.logger = this.getSLF4JLogger();
    }

    public static Desync getInstance() {
        if (instance == null) {


            throw new IllegalStateException("Desync plugin instance accessed before onEnable or after onDisable!");
        }
        return instance;
    }

    public EventScheduler getEventScheduler() {
        return eventScheduler;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public EventService getEventService() {
        if (eventService == null)
            throw new IllegalStateException("Attempted to get EventService but it was not initialized!");
        return eventService;
    }

    @Override
    public void onEnable() {
        instance = this;
        PluginDescriptionFile pdf = this.getDescription();
        logger.info("========================================");
        logger.info("Enabling {} v{}", pdf.getName(), pdf.getVersion());

        if (!setupProtocolLib()) {
            logger.error("Failed to hook into ProtocolLib! Disabling Desync.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        logger.info("Successfully hooked into ProtocolLib.");

        logger.info("Initializing managers and services...");
        cooldownManager = new CooldownManager(this);
        eventService = new EventService(this, cooldownManager);
        eventScheduler = new EventScheduler(this, eventService);

        logger.info("Loading static data...");
        SkinUtils.loadSkinProfile(this, logger);


        logger.info("Registering listeners...");
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new PlayerQuitListener(this, cooldownManager, eventService), this);

        logger.info("Registering commands...");
        try {

            Objects.requireNonNull(getCommand("desync")).setExecutor(new DesyncCommand(this));
        } catch (NullPointerException e) {
            logger.error("Failed to register command 'desync'! Is it defined in plugin.yml?", e);
        }

        logger.info("Starting tasks...");
        eventScheduler.start();

        logger.info(pdf.getName() + " enabled successfully.");
        logger.info("Things might get weird.");
        logger.info("========================================");
    }

    @Override
    public void onDisable() {
        PluginDescriptionFile pdf = this.getDescription();
        logger.info("========================================");
        logger.info("Disabling {}...", pdf.getName());

        logger.info("Stopping tasks...");
        try {
            if (eventScheduler != null) {
                eventScheduler.stop();
            }

            Bukkit.getScheduler().cancelTasks(this);
        } catch (Exception e) {
            logger.error("Error cancelling tasks during disable", e);
        }


        logger.info("Cleaning up event resources...");
        if (eventService != null) {
            eventService.cleanup();
        }

        logger.info("Saving data (if needed)...");


        if (cooldownManager != null) {
            cooldownManager.clearAllCooldowns();
        }


        logger.info("{} disabled.", pdf.getName());
        logger.info("Reality stabilizes... for now.");
        logger.info("========================================");
        instance = null;
        protocolManager = null;
        cooldownManager = null;
        eventService = null;
        eventScheduler = null;
    }

    private boolean setupProtocolLib() {
        if (protocolManager != null) return true;


        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            logger.error("ProtocolLib plugin not found! Is it installed and enabled?");
            return false;
        }
        if (!getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            logger.error("ProtocolLib plugin is not enabled!");
            return false;
        }
        try {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
            return this.protocolManager != null;
        } catch (Throwable t) {
            logger.error("Exception occurred while initializing ProtocolManager! Check ProtocolLib version compatibility.", t);
            return false;
        }
    }

    public ProtocolManager getProtocolManager() {
        if (protocolManager == null)
            throw new IllegalStateException("Attempted to get ProtocolManager but it was not initialized!");
        return protocolManager;
    }

    public Logger getPluginLogger() {
        return this.logger;
    }

}
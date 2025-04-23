package org.ladyluh.desync.utils;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.collect.Multimap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Utility class for obtaining WrappedGameProfiles with skin data for fake players,
 * including fetching specific skins via the Mojang API.
 */
public class SkinUtils {

    private static final String TARGET_SKIN_NICKNAME = "Joo";

    private static volatile WrappedGameProfile targetSkinProfile = null;
    private static volatile boolean targetSkinProfileLoaded = false;

    private SkinUtils() {}

    /**
     * Initiates the asynchronous loading of the target skin profile (e.g., for "Null").
     * Should be called once on plugin startup.
     *
     * @param plugin The main plugin instance to schedule tasks.
     * @param logger The plugin logger.
     */
    public static void loadSkinProfile(@NotNull JavaPlugin plugin, @NotNull Logger logger) {
        if (targetSkinProfileLoaded) {
            logger.info("Target skin profile loading already attempted.");
            return;
        }
        targetSkinProfileLoaded = true; // Mark loading as attempted

        logger.info("Attempting to load skin profile for nickname '{}' asynchronously...", TARGET_SKIN_NICKNAME);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID uuid = getUuidFromNickname(logger);

                if (uuid == null) {
                    logger.warn("Could not find UUID for nickname '{}' via Mojang API.", TARGET_SKIN_NICKNAME);
                    return;
                }

                WrappedGameProfile profile = getProfileWithProperties(uuid, logger);

                if (profile != null && profile.getProperties().containsKey("textures")) {
                    targetSkinProfile = profile;
                    logger.info("Successfully loaded skin profile for '{}' (UUID: {}) via Mojang API.", TARGET_SKIN_NICKNAME, uuid);
                } else {
                    logger.warn("Loaded profile for '{}' (UUID: {}), but no texture properties found. Using default skin.", TARGET_SKIN_NICKNAME, uuid);
                }

            } catch (Exception e) {
                logger.error("An unexpected error occurred during async skin profile loading for '{}'.", TARGET_SKIN_NICKNAME, e);
                targetSkinProfile = null;
            }
        });
    }

    /**
     * Fetches a UUID for a given nickname from the Mojang API.
     * This is a blocking network call and should be run asynchronously.
     *
     * @param logger The plugin logger.
     * @return The UUID, or null if not found or error occurs.
     */
    @Nullable
    private static UUID getUuidFromNickname(@NotNull Logger logger) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + SkinUtils.TARGET_SKIN_NICKNAME);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    JSONParser parser = new JSONParser();
                    JSONObject json = (JSONObject) parser.parse(reader);
                    String uuidString = (String) json.get("id");
                    uuidString = uuidString.replaceFirst(
                            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                            "$1-$2-$3-$4-$5");
                    return UUID.fromString(uuidString);
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                logger.info("Nickname '{}' not found via Mojang API (404).", SkinUtils.TARGET_SKIN_NICKNAME);
                return null;
            } else {
                logger.warn("Failed to get UUID for nickname '{}' from Mojang API. Response code: {}", SkinUtils.TARGET_SKIN_NICKNAME, responseCode);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error fetching UUID for nickname '{}' from Mojang API.", SkinUtils.TARGET_SKIN_NICKNAME, e);
            return null;
        }
    }

    /**
     * Fetches a GameProfile with skin properties for a given UUID from the Mojang Session Server API.
     * This is a blocking network call and should be run asynchronously.
     *
     * @param uuid   The UUID to look up.
     * @param logger The plugin logger.
     * @return The WrappedGameProfile with properties, or null if failed or no texture properties.
     */
    @Nullable
    private static WrappedGameProfile getProfileWithProperties(@NotNull UUID uuid, @NotNull Logger logger) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString() + "?unsigned=false");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    JSONParser parser = new JSONParser();
                    JSONObject json = (JSONObject) parser.parse(reader);
                    JSONArray propertiesArray = (JSONArray) json.get("properties");

                    if (propertiesArray == null) {
                        logger.info("Profile for UUID {} has no properties.", uuid);
                        return null;
                    }

                    WrappedGameProfile profile = new WrappedGameProfile(uuid, (String) json.get("name"));

                    for (Object propObj : propertiesArray) {
                        JSONObject propJson = (JSONObject) propObj;
                        String name = (String) propJson.get("name");
                        String value = (String) propJson.get("value");
                        String signature = (String) propJson.get("signature");

                        if ("textures".equals(name)) {
                            profile.getProperties().put(name, new WrappedSignedProperty(name, value, signature));
                            logger.info("Found and added 'textures' property for UUID {}.", uuid);
                        } else {
                            profile.getProperties().put(name, new WrappedSignedProperty(name, value, signature));
                        }
                    }
                    return profile;

                }
            } else {
                logger.warn("Failed to get profile for UUID {} from Session Server. Response code: {}", uuid, responseCode);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error fetching profile for UUID {} from Session Server.", uuid, e);
            return null;
        }
    }

    /**
     * Gets a WrappedGameProfile with skin data for a fake player.
     * Prioritizes the pre-loaded target skin, then a random online player's skin, then the default skin.
     *
     * @param targetPlayer The player who will see the fake entity. Used to exclude from online skin source selection.
     * @param logger       The plugin logger.
     * @param nickname     The profile's nickname
     * @return A WrappedGameProfile with skin data (or default).
     */
    @NotNull
    public static WrappedGameProfile getSkinProfile(@NotNull Player targetPlayer, @NotNull Logger logger, @NotNull String nickname) {
        if (targetSkinProfile != null) {
            UUID instanceUuid = UUID.randomUUID();
            WrappedGameProfile profile = new WrappedGameProfile(instanceUuid, nickname);
            for (WrappedSignedProperty property : targetSkinProfile.getProperties().values()) {
                profile.getProperties().put(property.getName(), property);
            }
            logger.info("Using pre-loaded target skin profile for stalker (UUID: {}).", instanceUuid);
            return profile;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        UUID stalkerUUID = UUID.randomUUID();

        Player skinSource = null;
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(targetPlayer))
                .collect(Collectors.toList());

        if (!candidates.isEmpty()) {
            skinSource = candidates.get(random.nextInt(candidates.size()));
            logger.info("Attempting to use random online player skin from: {}", skinSource.getName());

            try {
                WrappedGameProfile sourceWrappedProfile = WrappedGameProfile.fromPlayer(skinSource);
                Multimap<String, WrappedSignedProperty> sourceProperties = sourceWrappedProfile.getProperties();

                WrappedGameProfile profile = new WrappedGameProfile(stalkerUUID, skinSource.getName());

                if (sourceProperties.containsKey("textures")) {
                    Collection<WrappedSignedProperty> textureProperties = sourceProperties.get("textures");
                    if (!textureProperties.isEmpty()) {
                        WrappedSignedProperty textureProperty = textureProperties.iterator().next();
                        profile.getProperties().put("textures", textureProperty);
                        logger.info("Successfully applied random online player texture property to stalker profile (UUID: {}).", stalkerUUID);
                        return profile;
                    }
                }
                logger.info("Random online player {} has no texture properties. Falling back to default.", skinSource.getName());
            } catch (Exception e) {
                logger.error("Error getting skin from random online player {}. Falling back to default.", skinSource.getName(), e);
            }
        } else {
            logger.info("No other players online. Falling back to default skin.");
        }

        logger.info("Using default skin profile for stalker (UUID: {}).", stalkerUUID);
        return new WrappedGameProfile(stalkerUUID, nickname);
    }
}
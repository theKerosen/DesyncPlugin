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

    
    private static volatile boolean targetSkinProfileLoadAttempted = false;

    private SkinUtils() {}

    /**
     * Initiates the asynchronous loading of the target skin profile (for "Joo").
     * Should be called once on plugin startup.
     * Populates the static {@code targetSkinProfile} field if successful.
     *
     * @param plugin The main plugin instance to schedule tasks.
     * @param logger The plugin logger.
     */
    public static void loadSkinProfile(@NotNull JavaPlugin plugin, @NotNull Logger logger) {
        
        synchronized (SkinUtils.class) {
            if (targetSkinProfileLoadAttempted) {
                logger.debug("Target skin profile loading already attempted.");
                return;
            }
            targetSkinProfileLoadAttempted = true; 
        }


        logger.debug("Attempting to load target skin profile for nickname '{}' asynchronously...", TARGET_SKIN_NICKNAME);

        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                logger.debug("Async skin loading task started for '{}'.", TARGET_SKIN_NICKNAME);
                
                UUID uuid = getUuidFromNicknameInternal(logger); 

                if (uuid == null) {
                    logger.warn("Async skin loading for '{}' failed: Could not find UUID.", TARGET_SKIN_NICKNAME);
                    
                    return; 
                }
                logger.debug("Async skin loading for '{}': Found UUID {}.", TARGET_SKIN_NICKNAME, uuid);

                
                WrappedGameProfile profile = getProfileWithPropertiesInternal(uuid, logger); 

                
                if (profile != null && profile.getProperties().containsKey("textures") && !profile.getProperties().get("textures").isEmpty()) {
                    
                    
                    targetSkinProfile = profile;
                    logger.debug("Async skin loading for '{}' finished: Successfully loaded profile and textures.", TARGET_SKIN_NICKNAME);
                } else {
                    
                    
                    logger.warn("Async skin loading for '{}' finished: Profile loaded (UUID: {}) but has no texture properties or property fetch failed.", TARGET_SKIN_NICKNAME, uuid);
                    
                }

            } catch (Exception e) {
                
                logger.error("Async skin loading for '{}' failed unexpectedly.", TARGET_SKIN_NICKNAME, e);
                targetSkinProfile = null; 
            }
            
        });
    }

    /**
     * Fetches a UUID for a given nickname from the Mojang API.
     * This is a blocking network call intended for internal async use.
     *
     * @param logger The plugin logger.
     * @return The UUID, or null if not found or error occurs.
     */
    @Nullable
    private static UUID getUuidFromNicknameInternal(@NotNull Logger logger) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + TARGET_SKIN_NICKNAME); // Use TARGET_SKIN_NICKNAME constant
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
                    if (uuidString == null) {
                        logger.debug("Mojang API returned OK for '{}' but no 'id' field found.", TARGET_SKIN_NICKNAME);
                        return null;
                    }
                    uuidString = uuidString.replaceFirst(
                            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                            "$1-$2-$3-$4-$5");
                    return UUID.fromString(uuidString);
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                logger.debug("Nickname '{}' not found via Mojang API (404 response).", TARGET_SKIN_NICKNAME);
                return null;
            } else {
                logger.warn("Failed to get UUID for nickname '{}' from Mojang API. Response code: {}", TARGET_SKIN_NICKNAME, responseCode);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error fetching UUID for nickname '{}' from Mojang API.", TARGET_SKIN_NICKNAME, e);
            return null;
        }
    }

    /**
     * Fetches a GameProfile with skin properties for a given UUID from the Mojang Session Server API.
     * This is a blocking network call intended for internal async use.
     * Used by {@link #loadSkinProfile(JavaPlugin, Logger)} to get properties for the target skin.
     *
     * @param uuid   The UUID to look up.
     * @param logger The plugin logger.
     * @return The WrappedGameProfile with properties, or null if failed or no texture properties found.
     */
    @Nullable
    private static WrappedGameProfile getProfileWithPropertiesInternal(@NotNull UUID uuid, @NotNull Logger logger) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    JSONParser parser = new JSONParser();
                    JSONObject json = (JSONObject) parser.parse(reader);

                    String profileName = (String) json.get("name");
                    JSONArray propertiesArray = (JSONArray) json.get("properties");

                    if (propertiesArray == null || propertiesArray.isEmpty()) {
                        logger.debug("Profile for UUID {} has no properties.", uuid);
                        return new WrappedGameProfile(uuid, profileName);
                    }

                    WrappedGameProfile profile = new WrappedGameProfile(uuid, profileName);

                    boolean foundTextures = false;
                    for (Object propObj : propertiesArray) {
                        JSONObject propJson = (JSONObject) propObj;
                        String name = (String) propJson.get("name");
                        String value = (String) propJson.get("value");
                        String signature = (String) propJson.get("signature");

                        if ("textures".equals(name)) {
                            profile.getProperties().put(name, new WrappedSignedProperty(name, value, signature));
                            logger.debug("Found and added 'textures' property for UUID {}.", uuid);
                            foundTextures = true;
                        } else {
                            profile.getProperties().put(name, new WrappedSignedProperty(name, value, signature));
                            logger.debug("Added other property '{}' for UUID {}.", name, uuid);
                        }
                    }

                    if (foundTextures) {
                        return profile;
                    } else {
                        logger.debug("Profile for UUID {} was loaded, but the 'textures' property was not found.", uuid);
                        return profile;
                    }

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
     * Gets a WrappedGameProfile using the pre-loaded target skin ("Joo").
     * If the target skin is not available, it falls back directly to a profile with the default skin.
     * This method explicitly does NOT use online players as a fallback.
     * The returned profile will have the provided {@code nickname}.
     *
     * @param logger   The plugin logger.
     * @param nickname The desired nickname for the fake player profile (e.g., "Null").
     * @return A WrappedGameProfile with either the target skin or the default skin.
     */
    @NotNull
    public static WrappedGameProfile getTargetSkinOrDefaultProfile(@NotNull Logger logger, @NotNull String nickname) {
        
        UUID fakeEntityUUID = UUID.randomUUID();

        
        
        if (targetSkinProfile != null && targetSkinProfile.getProperties().containsKey("textures") && !targetSkinProfile.getProperties().get("textures").isEmpty()) {
            
            
            WrappedGameProfile profile = new WrappedGameProfile(fakeEntityUUID, nickname);

            
            
            Collection<WrappedSignedProperty> textureProperties = targetSkinProfile.getProperties().get("textures");
            
            if (!textureProperties.isEmpty()) {
                WrappedSignedProperty textureProperty = textureProperties.iterator().next();
                profile.getProperties().put("textures", new WrappedSignedProperty(textureProperty.getName(), textureProperty.getValue(), textureProperty.getSignature()));
                logger.debug("Using cached target skin profile (for '{}') for fake player (UUID: {}, Nickname: {}).", TARGET_SKIN_NICKNAME, fakeEntityUUID, nickname);
                return profile; 
            } else {
                
                logger.warn("Cached target skin profile (for '{}') unexpectedly had empty textures property list. Falling back to default.", TARGET_SKIN_NICKNAME);
            }
        }

        

        
        
        
        logger.debug("Target skin profile (for '{}') not cached or invalid. Using default skin profile for fake player (UUID: {}, Nickname: {}).", TARGET_SKIN_NICKNAME, fakeEntityUUID, nickname);
        return new WrappedGameProfile(fakeEntityUUID, nickname); 
    }

    
    
    
    @NotNull
    public static WrappedGameProfile getSkinProfile(@NotNull Player targetPlayer, @NotNull Logger logger, @NotNull String nickname) {
        UUID fakeEntityUUID = UUID.randomUUID(); 


        
        
        if (targetSkinProfile != null && targetSkinProfile.getProperties().containsKey("textures") && !targetSkinProfile.getProperties().get("textures").isEmpty()) {
            
            
            WrappedGameProfile profile = new WrappedGameProfile(fakeEntityUUID, nickname);

            
            
            Collection<WrappedSignedProperty> textureProperties = targetSkinProfile.getProperties().get("textures");
            if (!textureProperties.isEmpty()) {
                WrappedSignedProperty textureProperty = textureProperties.iterator().next();
                profile.getProperties().put("textures", new WrappedSignedProperty(textureProperty.getName(), textureProperty.getValue(), textureProperty.getSignature()));
                logger.debug("Using cached target skin profile (for '{}') for fake player (UUID: {}, Nickname: {}).", TARGET_SKIN_NICKNAME, fakeEntityUUID, nickname);
                return profile; 
            } else {
                
                logger.warn("Cached target skin profile (for '{}') unexpectedly had empty textures property list. Falling back to online players or default.", TARGET_SKIN_NICKNAME);
            }
        }

        

        
        logger.debug("Target skin profile (for '{}') not cached or invalid. Attempting to use a random online player's skin as fallback...", TARGET_SKIN_NICKNAME);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(targetPlayer))
                .collect(Collectors.toList());

        
        if (!candidates.isEmpty()) {
            Player skinSource = candidates.get(random.nextInt(candidates.size()));
            logger.debug("Attempting to use random online player skin from: {}", skinSource.getName());

            try {
                
                WrappedGameProfile sourceWrappedProfile = WrappedGameProfile.fromPlayer(skinSource);
                Multimap<String, WrappedSignedProperty> sourceProperties = sourceWrappedProfile.getProperties();

                
                WrappedGameProfile profile = new WrappedGameProfile(fakeEntityUUID, nickname);

                
                if (sourceProperties.containsKey("textures") && !sourceProperties.get("textures").isEmpty()) {
                    Collection<WrappedSignedProperty> textureProperties = sourceProperties.get("textures");
                    
                    WrappedSignedProperty textureProperty = textureProperties.iterator().next();
                    profile.getProperties().put("textures", new WrappedSignedProperty(textureProperty.getName(), textureProperty.getValue(), textureProperty.getSignature()));
                    logger.debug("Successfully applied random online player texture property to fake player profile (UUID: {}, Nickname: {}).", fakeEntityUUID, nickname);
                    return profile; 
                } else {
                    
                    logger.debug("Random online player {} has no texture properties. Falling back to default skin.", skinSource.getName());
                }
            } catch (Exception e) {
                
                logger.error("Error getting skin from random online player {}. Falling back to default.", skinSource.getName(), e);
                
            }
        } else {
            
            logger.debug("No other players online to use as fallback. Falling back to default skin.");
        }

        

        
        
        
        logger.debug("Using default skin profile for fake player (UUID: {}, Nickname: {}).", fakeEntityUUID, nickname);
        return new WrappedGameProfile(fakeEntityUUID, nickname); 
    }
}
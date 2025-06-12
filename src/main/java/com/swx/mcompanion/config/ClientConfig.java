package com.swx.mcompanion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "mcompanion_client.json";
    
    private static ClientConfigData configData = new ClientConfigData();
    
    public static class ClientConfigData {
        public String apiKey = "";
        public String serverUrl = "http://localhost:8123/api";
        public String chatEndpoint = "/ai/chat/apikey";
    }
    
    static {
        load();
    }
    
    public static void load() {
        try {
            Path configPath = getConfigPath();
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                configData = GSON.fromJson(json, ClientConfigData.class);
                if (configData == null) {
                    configData = new ClientConfigData();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load client config", e);
            configData = new ClientConfigData();
        }
    }
    
    public static void save() {
        try {
            Path configPath = getConfigPath();
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(configData);
            Files.writeString(configPath, json);
        } catch (Exception e) {
            LOGGER.error("Failed to save client config", e);
        }
    }
    
    private static Path getConfigPath() {
        return Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", CONFIG_FILE);
    }
    
    public static String getApiKey() {
        return configData.apiKey;
    }
    
    public static void setApiKey(String apiKey) {
        configData.apiKey = apiKey;
    }
    
    public static String getServerUrl() {
        return configData.serverUrl;
    }
    
    public static void setServerUrl(String serverUrl) {
        configData.serverUrl = serverUrl;
    }
    
    public static String getChatEndpoint() {
        return configData.chatEndpoint;
    }
    
    public static String getFullChatUrl() {
        String fullUrl = getServerUrl() + getChatEndpoint();
        LOGGER.info("使用的完整聊天URL: {}", fullUrl);
        return fullUrl;
    }
    
    public static void setChatEndpoint(String chatEndpoint) {
        configData.chatEndpoint = chatEndpoint;
    }
    
    public static void resetToDefaults() {
        configData = new ClientConfigData();
        save();
        LOGGER.info("配置已重置为默认值");
    }
} 
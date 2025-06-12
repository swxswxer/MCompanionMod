package com.swx.mcompanion.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.swx.mcompanion.config.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SimpleAiChatClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleAiChatClient.class);
    private static final Gson GSON = new Gson();
    private final String chatId;

    public SimpleAiChatClient() {
        this.chatId = UUID.randomUUID().toString();
    }

    public void sendMessage(String message, Consumer<String> onStreamData, Consumer<String> onComplete, Consumer<String> onError) {
        String apiKey = ClientConfig.getApiKey();
        if (apiKey.isEmpty()) {
            onError.accept("API Key未配置");
            return;
        }

        // 在新线程中执行网络请求，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                String urlString = ClientConfig.getFullChatUrl() + 
                    "?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8) +
                    "&chatId=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8);

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // 设置请求属性
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("X-API-Key", apiKey);
                connection.setConnectTimeout(30000); // 30秒连接超时
                connection.setReadTimeout(0); // 无限读取超时（用于流式响应）

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    String errorMsg = "HTTP错误: " + responseCode;
                    try (BufferedReader errorReader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder errorBody = new StringBuilder();
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            errorBody.append(line);
                        }
                        if (errorBody.length() > 0) {
                            errorMsg += " - " + errorBody.toString();
                        }
                    } catch (Exception e) {
                        LOGGER.warn("无法读取错误响应", e);
                    }
                    onError.accept(errorMsg);
                    return;
                }

                // 读取流式响应
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    
                    StringBuilder fullMessage = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        LOGGER.debug("接收到数据行: {}", line);
                        
                        // 只处理有实际数据的SSE行
                        if (line.startsWith("data:")) {
                            String data = line.substring(5); // 移除 "data:" 前缀
                            if (!data.equals("[DONE]")) {
                                // 处理空的data行 - 这些代表换行符
                                if (data.isEmpty()) {
                                    fullMessage.append("\n");
                                } else {
                                    // 直接拼接数据，完全保持原有格式
                                    fullMessage.append(data);
                                }
                                
                                // 临时调试：输出当前的完整消息用于检查
                                LOGGER.debug("当前消息长度: {}", fullMessage.length());
                                if (fullMessage.length() < 500) {
                                    LOGGER.debug("完整消息内容: [{}]", fullMessage.toString().replace("\n", "\\n"));
                                }
                                
                                // 实时更新显示完整消息
                                onStreamData.accept(fullMessage.toString());
                            }
                        }
                        // 忽略空行和注释行，它们只是SSE协议的分隔符
                    }
                    
                    // 完成回调
                    onComplete.accept(fullMessage.toString());
                    
                } catch (IOException e) {
                    LOGGER.error("读取响应流失败", e);
                    onError.accept("读取响应失败: " + e.getMessage());
                }

            } catch (Exception e) {
                LOGGER.error("发送请求失败", e);
                onError.accept("发送请求失败: " + e.getMessage());
            }
        });
    }

    public void close() {
        // 简单实现不需要特殊的关闭操作
    }
} 
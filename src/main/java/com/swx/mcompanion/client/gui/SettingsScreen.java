package com.swx.mcompanion.client.gui;

import com.swx.mcompanion.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class SettingsScreen extends Screen {
    private final Screen parent;
    private EditBox apiKeyField;
    private Button saveButton;
    private Button cancelButton;
    private String statusMessage = "";
    private long statusMessageTime = 0;

    public SettingsScreen(Screen parent) {
        super(Component.literal("AI Chat 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // API Key 输入框
        this.apiKeyField = new EditBox(this.font, centerX - 100, centerY - 30, 200, 20, 
                Component.literal("API Key"));
        this.apiKeyField.setMaxLength(256);
        this.apiKeyField.setValue(ClientConfig.getApiKey());
        this.apiKeyField.setBordered(true);
        this.apiKeyField.setCanLoseFocus(false);
        this.apiKeyField.setFocused(true);
        this.apiKeyField.setVisible(true);
        this.apiKeyField.setEditable(true);
        this.addRenderableWidget(apiKeyField);

        // 保存按钮
        this.saveButton = Button.builder(Component.literal("保存"), button -> saveSettings())
                .bounds(centerX - 50, centerY + 10, 45, 20)
                .build();
        this.addRenderableWidget(saveButton);

        // 取消按钮
        this.cancelButton = Button.builder(Component.literal("取消"), button -> this.minecraft.setScreen(parent))
                .bounds(centerX + 5, centerY + 10, 45, 20)
                .build();
        this.addRenderableWidget(cancelButton);

        this.setInitialFocus(apiKeyField);
    }

    private void saveSettings() {
        String apiKey = apiKeyField.getValue().trim();
        
        if (apiKey.isEmpty()) {
            showStatusMessage("API Key不能为空", true);
            return;
        }
        
        ClientConfig.setApiKey(apiKey);
        ClientConfig.save();
        showStatusMessage("保存成功", false);
        
        // 1秒后返回
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                this.minecraft.execute(() -> this.minecraft.setScreen(parent));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void showStatusMessage(String message, boolean isError) {
        this.statusMessage = message;
        this.statusMessageTime = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();
        apiKeyField.tick();
        
        // 清除状态消息
        if (System.currentTimeMillis() - statusMessageTime > 3000) {
            statusMessage = "";
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 绘制背景框
        guiGraphics.fill(centerX - 120, centerY - 60, centerX + 120, centerY + 60, 0xCC000000);
        guiGraphics.fill(centerX - 119, centerY - 59, centerX + 119, centerY + 59, 0x44FFFFFF);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, centerX, centerY - 50, 0xFFFFFF);

        // API Key 标签 - 使用阴影效果
        guiGraphics.drawString(this.font, "API Key:", centerX - 100, centerY - 45, 0xFFFFFF, true);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 状态消息
        if (!statusMessage.isEmpty()) {
            int color = statusMessage.contains("成功") ? 0x00FF00 : 0xFF0000;
            guiGraphics.drawCenteredString(this.font, Component.literal(statusMessage), centerX, centerY + 35, color);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 优先处理输入框的按键事件
        if (apiKeyField != null && apiKeyField.isFocused()) {
            if (keyCode == 257) { // Enter键
                saveSettings();
                return true;
            } else if (apiKeyField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        

        
        if (keyCode == 256) { // ESC键
            this.minecraft.setScreen(parent);
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        
        // 如果没有组件处理点击事件，设置输入框焦点
        if (!result) {
            if (apiKeyField != null) {
                this.setFocused(apiKeyField);
                apiKeyField.setFocused(true);
                result = true;
            }
        }
        
        return result;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
} 
package com.swx.mcompanion.client.gui;

import com.swx.mcompanion.client.network.SimpleAiChatClient;
import com.swx.mcompanion.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AiChatScreen extends Screen {
    // 布局常量
    private static final int SIDEBAR_WIDTH = 180; // 调整侧边栏宽度，大约1:5比例
    private static final int MARGIN = 8;
    private static final int INPUT_HEIGHT = 32;
    private static final int SEND_BUTTON_WIDTH = 60;
    private static final int SETTINGS_BUTTON_SIZE = 24;
    
    // 颜色常量
    private static final int SIDEBAR_BG = 0xE0202123;
    private static final int CHAT_BG = 0xE0FFFFFF;
    private static final int INPUT_BG = 0xFFFFFFFF;
    private static final int BORDER_COLOR = 0xFF404040;
    private static final int USER_MSG_BG = 0xFFDCF8C6;
    private static final int AI_MSG_BG = 0xFFF1F3F4;
    private static final int SIDEBAR_ITEM_BG = 0xFF2A2D31;
    private static final int SIDEBAR_ITEM_HOVER = 0xFF404248;
    
    // UI组件
    private EditBox inputField;
    private Button sendButton;
    private Button settingsButton;
    private Button newChatButton;
    
    // 聊天数据
    private List<ChatMessage> currentChat;
    private List<ChatSession> chatSessions;
    private int activeChatIndex = 0;
    private int sidebarScrollOffset = 0;
    private int chatScrollOffset = 0;
    private boolean isWaitingForResponse = false;
    
    private SimpleAiChatClient chatClient;

    // 聊天消息类
    public static class ChatMessage {
        public final String content;
        public final boolean isUser;
        public final long timestamp;
        private String processedContent; // 缓存预处理后的内容
        
        public ChatMessage(String content, boolean isUser) {
            this.content = content;
            this.isUser = isUser;
            this.timestamp = System.currentTimeMillis();
            this.processedContent = null; // 初始为null，第一次访问时处理
        }
        
        public String getProcessedContent() {
            if (processedContent == null) {
                processedContent = preprocessMessage(content);
            }
            return processedContent;
        }
        
        private static String preprocessMessage(String content) {
            if (content == null || content.isEmpty()) {
                return content;
            }
            
            // 确保每个以"   -"开头的行都在新行开始
            content = content.replaceAll("(\\s{2,})-\\s", "\n- ");
            
            // 确保数字列表项正确换行
            content = content.replaceAll("(\\d+\\.)\\s", "\n$1 ");
            
            // 处理**标题**格式
            content = content.replaceAll("(\\*\\*[^*]+\\*\\*)：", "\n$1：");
            
            return content;
        }
    }
    
    // 聊天会话类
    public static class ChatSession {
        public final String id;
        public String title;
        public final List<ChatMessage> messages;
        public final long createdTime;
        
        public ChatSession(String id, String title) {
            this.id = id;
            this.title = title;
            this.messages = new ArrayList<>();
            this.createdTime = System.currentTimeMillis();
        }
        
        public String getPreview() {
            if (messages.isEmpty()) return "新对话";
            return messages.get(0).content.length() > 30 ? 
                messages.get(0).content.substring(0, 30) + "..." : 
                messages.get(0).content;
        }
    }

    public AiChatScreen() {
        super(Component.literal("AI Assistant"));
        this.chatSessions = new ArrayList<>();
        this.chatClient = new SimpleAiChatClient();
        
        // 创建默认聊天会话
        this.chatSessions.add(new ChatSession("default", "新对话"));
        this.currentChat = this.chatSessions.get(0).messages;
    }

    @Override
    protected void init() {
        super.init();
        
        // 侧边栏新建对话按钮
        this.newChatButton = Button.builder(Component.literal("+ 新对话"), button -> createNewChat())
                .bounds(MARGIN, MARGIN, SIDEBAR_WIDTH - 2 * MARGIN, 28)
                .build();
        this.addRenderableWidget(newChatButton);
        
        // 设置按钮
        this.settingsButton = Button.builder(Component.literal("⚙"), button -> openSettings())
                .bounds(SIDEBAR_WIDTH + MARGIN, MARGIN, SETTINGS_BUTTON_SIZE, SETTINGS_BUTTON_SIZE)
                .build();
        this.addRenderableWidget(settingsButton);
        
        // 计算聊天区域
        int chatAreaX = SIDEBAR_WIDTH + MARGIN;
        int chatAreaWidth = this.width - SIDEBAR_WIDTH - 2 * MARGIN;
        int inputY = this.height - MARGIN - INPUT_HEIGHT;
        
        // 输入框
        this.inputField = new EditBox(this.font, chatAreaX, inputY, 
                chatAreaWidth - SEND_BUTTON_WIDTH - MARGIN, INPUT_HEIGHT, 
                Component.literal("输入你的问题..."));
        this.inputField.setMaxLength(1000);
        this.inputField.setBordered(true);
        this.inputField.setCanLoseFocus(false);
        this.inputField.setFocused(true);
        this.inputField.setVisible(true);
        this.inputField.setEditable(true);
        // 使用addRenderableWidget而不是addWidget，确保能正确渲染
        this.addRenderableWidget(inputField);
        
        // 发送按钮
        this.sendButton = Button.builder(Component.literal("发送"), button -> sendMessage())
                .bounds(this.width - MARGIN - SEND_BUTTON_WIDTH, inputY, SEND_BUTTON_WIDTH, INPUT_HEIGHT)
                .build();
        this.addRenderableWidget(sendButton);
        
        // 设置输入框焦点
        this.setInitialFocus(inputField);
    }

    private void createNewChat() {
        String newId = "chat_" + System.currentTimeMillis();
        ChatSession newSession = new ChatSession(newId, "新对话");
        chatSessions.add(0, newSession);
        activeChatIndex = 0;
        currentChat = newSession.messages;
        chatScrollOffset = 0;
    }

    private void openSettings() {
        this.minecraft.setScreen(new SettingsScreen(this));
    }

    private void sendMessage() {
        String message = inputField.getValue().trim();
        if (message.isEmpty() || isWaitingForResponse) {
            return;
        }
        
        // 检查是否设置了API Key
        if (ClientConfig.getApiKey().isEmpty()) {
            addMessage("请先在设置中配置API Key", false);
            return;
        }
        
        // 添加用户消息
        addMessage(message, true);
        inputField.setValue("");
        
        // 更新会话标题（使用第一条消息）
        ChatSession currentSession = chatSessions.get(activeChatIndex);
        if (currentSession.messages.size() == 1) {
            currentSession.title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
        }
        
        // 设置等待状态
        isWaitingForResponse = true;
        sendButton.setMessage(Component.literal("发送中..."));
        
        // 添加AI回复占位符
        addMessage("", false);
        
        // 发送请求
        chatClient.sendMessage(message, this::onStreamData, this::onComplete, this::onError);
    }
    
    private void onStreamData(String data) {
        // 实时更新最后一条消息（AI回复）
        if (!currentChat.isEmpty()) {
            ChatMessage lastMsg = currentChat.get(currentChat.size() - 1);
            if (!lastMsg.isUser) {
                currentChat.set(currentChat.size() - 1, new ChatMessage(data, false));
            }
        }
    }
    
    private void onComplete(String finalMessage) {
        // 确保最终消息正确显示
        if (!currentChat.isEmpty()) {
            ChatMessage lastMsg = currentChat.get(currentChat.size() - 1);
            if (!lastMsg.isUser) {
                currentChat.set(currentChat.size() - 1, new ChatMessage(finalMessage, false));
            }
        }
        isWaitingForResponse = false;
        sendButton.setMessage(Component.literal("发送"));
    }
    
    private void onError(String error) {
        addMessage("错误: " + error, false);
        isWaitingForResponse = false;
        sendButton.setMessage(Component.literal("发送"));
    }
    
    private void addMessage(String message, boolean isUser) {
        currentChat.add(new ChatMessage(message, isUser));
        // 自动滚动到底部
        scrollToBottom();
    }
    
    private void scrollToBottom() {
        int totalHeight = calculateChatContentHeight();
        int chatAreaHeight = this.height - 100; // 减去顶部和底部的空间
        chatScrollOffset = Math.max(0, totalHeight - chatAreaHeight);
    }
    
    private int calculateChatContentHeight() {
        int height = 0;
        int chatAreaWidth = this.width - SIDEBAR_WIDTH - 4 * MARGIN;
        
        for (ChatMessage msg : currentChat) {
            if (msg.content.isEmpty()) continue;
            
            // 预处理消息内容，确保正确的换行
            String processedContent = msg.getProcessedContent();
            
            // 正确计算包含换行符的消息高度
            String[] paragraphs = processedContent.split("\n", -1); // 使用-1保留空字符串
            int totalHeight = 0;
            
            for (String paragraph : paragraphs) {
                if (paragraph.trim().isEmpty()) {
                    totalHeight += 12; // 空行高度
                } else {
                    List<FormattedCharSequence> lines = this.font.split(Component.literal(paragraph), 
                            chatAreaWidth - 40);
                    totalHeight += lines.size() * 12;
                }
            }
            height += totalHeight + 24; // 16 padding + 8 消息间距
        }
        return height;
    }

    @Override
    public void tick() {
        super.tick();
        if (inputField != null) {
            inputField.tick();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 背景
        this.renderBackground(guiGraphics);
        
        // 绘制侧边栏
        renderSidebar(guiGraphics, mouseX, mouseY);
        
        // 绘制聊天区域
        renderChatArea(guiGraphics, mouseX, mouseY);
        
        // 绘制输入区域
        renderInputArea(guiGraphics);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 侧边栏背景
        guiGraphics.fill(0, 0, SIDEBAR_WIDTH, this.height, SIDEBAR_BG);
        
        // 侧边栏边框
        guiGraphics.fill(SIDEBAR_WIDTH - 1, 0, SIDEBAR_WIDTH, this.height, BORDER_COLOR);
        
        // 绘制聊天历史列表
        int y = 60; // 从新建按钮下方开始
        for (int i = 0; i < chatSessions.size(); i++) {
            ChatSession session = chatSessions.get(i);
            int itemHeight = 50;
            
            // 检查鼠标悬停
            boolean isHovered = mouseX >= MARGIN && mouseX <= SIDEBAR_WIDTH - MARGIN && 
                              mouseY >= y && mouseY <= y + itemHeight;
            boolean isActive = i == activeChatIndex;
            
            // 绘制项目背景
            int bgColor = isActive ? SIDEBAR_ITEM_HOVER : (isHovered ? SIDEBAR_ITEM_HOVER : SIDEBAR_ITEM_BG);
            guiGraphics.fill(MARGIN, y, SIDEBAR_WIDTH - MARGIN, y + itemHeight, bgColor);
            
            // 绘制会话标题 - 使用高清渲染
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(1.0f, 1.0f, 1.0f);
            guiGraphics.drawString(this.font, session.title, MARGIN + 8, y + 8, 0xFFFFFFFF, false);
            guiGraphics.pose().popPose();
            
            // 绘制预览 - 使用高清渲染
            String preview = session.getPreview();
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(1.0f, 1.0f, 1.0f);
            guiGraphics.drawString(this.font, preview, MARGIN + 8, y + 22, 0xFFCCCCCC, false);
            guiGraphics.pose().popPose();
            
            y += itemHeight + 4;
        }
    }
    
    private void renderChatArea(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int chatX = SIDEBAR_WIDTH + MARGIN;
        int chatY = MARGIN + 30; // 设置按钮下方
        int chatWidth = this.width - SIDEBAR_WIDTH - 2 * MARGIN;
        int chatHeight = this.height - chatY - INPUT_HEIGHT - 2 * MARGIN;
        
        // 聊天区域背景
        guiGraphics.fill(chatX, chatY, chatX + chatWidth, chatY + chatHeight, CHAT_BG);
        
        // 绘制消息
        int y = chatY + MARGIN - chatScrollOffset;
        int messageWidth = chatWidth - 2 * MARGIN;
        
        for (ChatMessage message : currentChat) {
            if (message.content.isEmpty()) continue;
            
            // 预处理消息内容，确保正确的换行
            String processedContent = message.getProcessedContent();
            
            // 分割消息内容为多行，正确处理换行符
            String[] paragraphs = processedContent.split("\n", -1); // 使用-1保留空字符串
            int totalHeight = 0;
            
            // 计算总高度
            for (String paragraph : paragraphs) {
                if (paragraph.trim().isEmpty()) {
                    totalHeight += 12; // 空行高度
                } else {
                    List<FormattedCharSequence> lines = this.font.split(Component.literal(paragraph), 
                            messageWidth - 40);
                    totalHeight += lines.size() * 12;
                }
            }
            totalHeight += 16; // padding
            
            // 只绘制可见的消息
            if (y + totalHeight > chatY && y < chatY + chatHeight) {
                // 消息背景
                int msgBg = message.isUser ? USER_MSG_BG : AI_MSG_BG;
                int msgX = message.isUser ? chatX + chatWidth - messageWidth + 20 : chatX + 20;
                guiGraphics.fill(msgX, y, msgX + messageWidth - 40, y + totalHeight, msgBg);
                
                // 绘制消息内容 - 使用Component渲染以获得更好的清晰度
                int lineY = y + 8;
                int textColor = 0xFF000000; // 纯黑色文字
                
                for (String paragraph : paragraphs) {
                    if (paragraph.trim().isEmpty()) {
                        lineY += 12; // 空行，直接跳过一行
                    } else {
                        List<FormattedCharSequence> lines = this.font.split(Component.literal(paragraph), 
                                messageWidth - 40);
                        for (FormattedCharSequence line : lines) {
                            // 使用高清文字渲染 - Minecraft原生方式
                            guiGraphics.pose().pushPose();
                            guiGraphics.pose().scale(1.0f, 1.0f, 1.0f); // 确保1:1比例
                            guiGraphics.drawString(this.font, line, msgX + 8, lineY, textColor, false); // 不使用阴影
                            guiGraphics.pose().popPose();
                            lineY += 12;
                        }
                    }
                }
            }
            
            y += totalHeight + 8;
        }
    }
    
    private void renderInputArea(GuiGraphics guiGraphics) {
        int inputAreaY = this.height - INPUT_HEIGHT - 2 * MARGIN;
        int chatX = SIDEBAR_WIDTH + MARGIN;
        int chatWidth = this.width - SIDEBAR_WIDTH - 2 * MARGIN;
        
        // 输入区域背景
        guiGraphics.fill(chatX, inputAreaY, chatX + chatWidth, this.height - MARGIN, INPUT_BG);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 优先处理输入框的按键事件
        if (inputField != null && inputField.isFocused()) {
            if (keyCode == 257 && !inputField.getValue().trim().isEmpty()) { // Enter键
                sendMessage();
                return true;
            } else if (inputField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否点击了侧边栏的聊天项目
        if (mouseX >= MARGIN && mouseX <= SIDEBAR_WIDTH - MARGIN && mouseY >= 60) {
            int itemIndex = ((int)mouseY - 60) / 54; // 50像素高度 + 4像素间距
            if (itemIndex >= 0 && itemIndex < chatSessions.size()) {
                activeChatIndex = itemIndex;
                currentChat = chatSessions.get(itemIndex).messages;
                chatScrollOffset = 0;
                return true;
            }
        }
        
        // 先调用父类方法处理所有组件的点击
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        
        // 如果没有组件处理点击事件，设置输入框焦点
        if (!result && inputField != null) {
            this.setFocused(inputField);
            inputField.setFocused(true);
            result = true;
        }
        
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX > SIDEBAR_WIDTH) {
            // 聊天区域滚动
            int totalHeight = calculateChatContentHeight();
            int chatAreaHeight = this.height - 100;
            int maxScroll = Math.max(0, totalHeight - chatAreaHeight);
            chatScrollOffset = Math.max(0, Math.min(maxScroll, chatScrollOffset - (int)(delta * 20)));
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void onClose() {
        super.onClose();
        if (chatClient != null) {
            chatClient.close();
        }
    }
} 
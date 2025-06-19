package com.swx.mcompanion.client.gui;

import com.swx.mcompanion.client.network.SimpleAiChatClient;
import com.swx.mcompanion.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
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
        private FormattedContent formattedContent; // 缓存格式化后的内容
        
        public ChatMessage(String content, boolean isUser) {
            this.content = content;
            this.isUser = isUser;
            this.timestamp = System.currentTimeMillis();
            this.formattedContent = null; // 初始为null，第一次访问时处理
        }
        
        public FormattedContent getFormattedContent() {
            if (formattedContent == null) {
                formattedContent = MarkdownParser.parseMarkdown(content);
            }
            return formattedContent;
        }
    }
    
    // 格式化内容类
    public static class FormattedContent {
        public final List<MarkdownElement> elements;
        public final int totalHeight;
        
        public FormattedContent(List<MarkdownElement> elements, int totalHeight) {
            this.elements = elements;
            this.totalHeight = totalHeight;
        }
    }
    
    // Markdown元素基类
    public static abstract class MarkdownElement {
        public final String text;
        public final int indentLevel;
        
        public MarkdownElement(String text, int indentLevel) {
            this.text = text;
            this.indentLevel = indentLevel;
        }
        
        public abstract int getHeight();
        public abstract int getTextColor();
        public abstract void render(GuiGraphics guiGraphics, Font font, int x, int y, int maxWidth);
    }
    
    // 标题元素
    public static class HeadingElement extends MarkdownElement {
        public final int level;
        
        public HeadingElement(String text, int level) {
            super(text, 0);
            this.level = level;
        }
        
        @Override
        public int getHeight() { return 24; }
        
        @Override
        public int getTextColor() { return 0xFF2E7D32; }
        
        @Override
        public void render(GuiGraphics guiGraphics, Font font, int x, int y, int maxWidth) {
            guiGraphics.pose().pushPose();
            float scale = level == 1 ? 1.2f : 1.1f;
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.drawString(font, text, (int)(x / scale), (int)(y / scale), getTextColor(), false);
            guiGraphics.pose().popPose();
        }
    }
    
    // 段落元素
    public static class ParagraphElement extends MarkdownElement {
        private final List<TextFragment> fragments;
        private List<FormattedCharSequence> wrappedLines;
        private int calculatedHeight;
        
        public ParagraphElement(String text, int indentLevel) {
            super(text, indentLevel);
            this.fragments = parseTextFragments(text);
        }
        
        public void calculateWrapping(Font font, int maxWidth) {
            if (wrappedLines != null) return; // 已经计算过
            
            wrappedLines = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();
            
            for (TextFragment fragment : fragments) {
                currentLine.append(fragment.text);
            }
            
            // 使用Minecraft的自动换行功能
            int effectiveWidth = Math.max(100, maxWidth - (indentLevel * 16));
            List<FormattedCharSequence> lines = font.split(Component.literal(currentLine.toString()), effectiveWidth);
            wrappedLines.addAll(lines);
            
            calculatedHeight = wrappedLines.size() * 12 + 4; // 每行12像素 + 间距
        }
        
        @Override
        public int getHeight() { 
            return calculatedHeight > 0 ? calculatedHeight : 14; 
        }
        
        @Override
        public int getTextColor() { return 0xFF000000; }
        
        @Override
        public void render(GuiGraphics guiGraphics, Font font, int x, int y, int maxWidth) {
            calculateWrapping(font, maxWidth);
            
            int currentY = y;
            int indentX = x + (indentLevel * 16);
            
            for (FormattedCharSequence line : wrappedLines) {
                guiGraphics.drawString(font, line, indentX, currentY, getTextColor(), false);
                currentY += 12;
            }
        }
        
        private List<TextFragment> parseTextFragments(String text) {
            List<TextFragment> fragments = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inBold = false, inItalic = false, inCode = false;
            
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                
                if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                    // 粗体标记
                    if (current.length() > 0) {
                        fragments.add(new TextFragment(current.toString(), inBold, inItalic, inCode));
                        current.setLength(0);
                    }
                    inBold = !inBold;
                    i++; // 跳过第二个*
                } else if (c == '*') {
                    // 斜体标记
                    if (current.length() > 0) {
                        fragments.add(new TextFragment(current.toString(), inBold, inItalic, inCode));
                        current.setLength(0);
                    }
                    inItalic = !inItalic;
                } else if (c == '`') {
                    // 代码标记
                    if (current.length() > 0) {
                        fragments.add(new TextFragment(current.toString(), inBold, inItalic, inCode));
                        current.setLength(0);
                    }
                    inCode = !inCode;
                } else {
                    current.append(c);
                }
            }
            
            if (current.length() > 0) {
                fragments.add(new TextFragment(current.toString(), inBold, inItalic, inCode));
            }
            
            return fragments;
        }
    }
    
    // 列表元素
    public static class ListElement extends MarkdownElement {
        public final boolean isOrdered;
        public final int number;
        private List<FormattedCharSequence> wrappedLines;
        private int calculatedHeight;
        
        public ListElement(String text, boolean isOrdered, int number, int indentLevel) {
            super(text, indentLevel);
            this.isOrdered = isOrdered;
            this.number = number;
        }
        
        public void calculateWrapping(Font font, int maxWidth) {
            if (wrappedLines != null) return; // 已经计算过
            
            String prefix = isOrdered ? (number + ". ") : "• ";
            int prefixWidth = font.width(prefix);
            int effectiveWidth = Math.max(100, maxWidth - (indentLevel * 20) - prefixWidth);
            
            wrappedLines = font.split(Component.literal(text), effectiveWidth);
            calculatedHeight = wrappedLines.size() * 12 + 4;
        }
        
        @Override
        public int getHeight() { 
            return calculatedHeight > 0 ? calculatedHeight : 14; 
        }
        
        @Override
        public int getTextColor() { return 0xFF1976D2; }
        
        @Override
        public void render(GuiGraphics guiGraphics, Font font, int x, int y, int maxWidth) {
            calculateWrapping(font, maxWidth);
            
            int indentX = x + (indentLevel * 20);
            String prefix = isOrdered ? (number + ". ") : "• ";
            int prefixWidth = font.width(prefix);
            
            // 绘制前缀
            guiGraphics.drawString(font, prefix, indentX, y, getTextColor(), false);
            
            // 绘制换行后的文本
            int currentY = y;
            for (FormattedCharSequence line : wrappedLines) {
                guiGraphics.drawString(font, line, indentX + prefixWidth, currentY, 0xFF000000, false);
                currentY += 12;
            }
        }
    }
    
    // 代码块元素
    public static class CodeBlockElement extends MarkdownElement {
        public CodeBlockElement(String text) {
            super(text, 1);
        }
        
        @Override
        public int getHeight() { return 16; }
        
        @Override
        public int getTextColor() { return 0xFFD32F2F; }
        
        @Override
        public void render(GuiGraphics guiGraphics, Font font, int x, int y, int maxWidth) {
            int codeX = x + 20;
            int textWidth = font.width(text);
            
            // 绘制代码背景
            guiGraphics.fill(codeX - 4, y - 2, codeX + textWidth + 4, y + 14, 0xFFF5F5F5);
            guiGraphics.fill(codeX - 4, y - 2, codeX - 2, y + 14, 0xFF2196F3); // 左边蓝色边线
            
            guiGraphics.drawString(font, text, codeX, y, getTextColor(), false);
        }
    }
    
    // 空行元素
    public static class EmptyLineElement extends MarkdownElement {
        public EmptyLineElement() {
            super("", 0);
        }
        
        @Override
        public int getHeight() { return 10; }
        
        @Override
        public int getTextColor() { return 0; }
        
        @Override
        public void render(GuiGraphics guiGraphics, Font font, int x, int y, int maxWidth) {
            // 空行不渲染任何内容
        }
    }
    
    // 分隔线元素
    public static class SeparatorElement extends MarkdownElement {
        public SeparatorElement() {
            super("", 0);
        }
        
        @Override
        public int getHeight() { return 20; }
        
        @Override
        public int getTextColor() { return 0; }
        
        @Override
        public void render(GuiGraphics guiGraphics, Font font, int x, int y, int maxWidth) {
            int lineY = y + 8;
            guiGraphics.fill(x, lineY, x + maxWidth - 40, lineY + 1, 0xFF888888);
        }
    }
    
    // 文本片段类
    public static class TextFragment {
        public final String text;
        public final boolean isBold;
        public final boolean isItalic;
        public final boolean isCode;
        
        public TextFragment(String text, boolean isBold, boolean isItalic, boolean isCode) {
            this.text = text;
            this.isBold = isBold;
            this.isItalic = isItalic;
            this.isCode = isCode;
        }
    }
    
    // Markdown解析器
    public static class MarkdownParser {
        
        public static FormattedContent parseMarkdown(String content) {
            if (content == null || content.isEmpty()) {
                return new FormattedContent(new ArrayList<>(), 0);
            }
            
            List<MarkdownElement> elements = new ArrayList<>();
            String[] lines = content.split("\n");
            boolean inCodeBlock = false;
            int listNumber = 1;
            
            for (String line : lines) {
                // 处理代码块
                if (line.trim().startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    if (!inCodeBlock) {
                        elements.add(new EmptyLineElement());
                    }
                    continue;
                }
                
                if (inCodeBlock) {
                    elements.add(new CodeBlockElement(line));
                    continue;
                }
                
                // 空行
                if (line.trim().isEmpty()) {
                    elements.add(new EmptyLineElement());
                    listNumber = 1; // 重置列表编号
                    continue;
                }
                
                // 分隔线
                if (line.trim().matches("^[-*_]{3,}$")) {
                    elements.add(new SeparatorElement());
                    continue;
                }
                
                // 标题
                if (line.trim().startsWith("#")) {
                    int level = 0;
                    while (level < line.length() && line.charAt(level) == '#') {
                        level++;
                    }
                    String title = line.substring(level).trim();
                    elements.add(new HeadingElement(title, level));
                    continue;
                }
                
                // 有序列表
                if (line.trim().matches("^\\d+\\.\\s+.*")) {
                    int indent = getIndentLevel(line);
                    String text = line.trim().replaceFirst("^\\d+\\.\\s+", "");
                    elements.add(new ListElement(text, true, listNumber++, indent));
                    continue;
                }
                
                // 无序列表
                if (line.trim().matches("^[-*+]\\s+.*")) {
                    int indent = getIndentLevel(line);
                    String text = line.trim().replaceFirst("^[-*+]\\s+", "");
                    elements.add(new ListElement(text, false, 0, indent));
                    continue;
                }
                
                // 缩进列表项
                if (line.matches("^\\s{2,}[-*+]\\s+.*")) {
                    int indent = getIndentLevel(line) + 1;
                    String text = line.trim().replaceFirst("^[-*+]\\s+", "");
                    elements.add(new ListElement(text, false, 0, indent));
                    continue;
                }
                
                // 普通段落
                int indent = getIndentLevel(line);
                elements.add(new ParagraphElement(line.trim(), indent));
            }
            
            // 高度在渲染时动态计算
            return new FormattedContent(elements, 0);
        }
        
        // 动态计算内容高度
        public static int calculateContentHeight(List<MarkdownElement> elements, Font font, int maxWidth) {
            int totalHeight = 16; // 基础padding
            
            for (MarkdownElement element : elements) {
                if (element instanceof ParagraphElement) {
                    ((ParagraphElement) element).calculateWrapping(font, maxWidth);
                } else if (element instanceof ListElement) {
                    ((ListElement) element).calculateWrapping(font, maxWidth);
                }
                totalHeight += element.getHeight();
            }
            
            return totalHeight;
        }
        
        private static int getIndentLevel(String line) {
            int indent = 0;
            for (char c : line.toCharArray()) {
                if (c == ' ') indent++;
                else if (c == '\t') indent += 4;
                else break;
            }
            return indent / 4; // 每4个空格算一级缩进
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
        int chatY = MARGIN + 30;
        int chatHeight = this.height - chatY - INPUT_HEIGHT - 2 * MARGIN;
        
        // 确保滚动到真正的底部
        chatScrollOffset = Math.max(0, totalHeight - chatHeight + 20); // 额外20像素缓冲
    }
    
    private int calculateChatContentHeight() {
        int height = 16; // 基础padding
        int chatAreaWidth = this.width - SIDEBAR_WIDTH - 4 * MARGIN;
        int messageWidth = chatAreaWidth - 40; // 消息框的有效宽度
        
        for (ChatMessage msg : currentChat) {
            if (msg.content.isEmpty()) continue;
            
            FormattedContent formattedContent = msg.getFormattedContent();
            
            // 动态计算真实高度
            int contentHeight = MarkdownParser.calculateContentHeight(
                formattedContent.elements, this.font, messageWidth);
            
            height += contentHeight + 16; // 消息间距
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
            
            FormattedContent formattedContent = message.getFormattedContent();
            List<MarkdownElement> elements = formattedContent.elements;
            
            // 预计算所有元素的换行和高度
            int totalHeight = MarkdownParser.calculateContentHeight(elements, this.font, messageWidth - 40);
            
            // 只绘制可见的消息
            if (y + totalHeight > chatY && y < chatY + chatHeight) {
                // 消息背景
                int msgBg = message.isUser ? USER_MSG_BG : AI_MSG_BG;
                int msgX = message.isUser ? chatX + chatWidth - messageWidth + 20 : chatX + 20;
                guiGraphics.fill(msgX, y, msgX + messageWidth - 40, y + totalHeight, msgBg);
                
                // 绘制消息内容
                int lineY = y + 8;
                
                for (MarkdownElement element : elements) {
                    if (element instanceof EmptyLineElement) {
                        lineY += element.getHeight();
                        continue;
                    }
                    
                    if (element instanceof SeparatorElement) {
                        // 绘制分隔线
                        int separatorY = lineY + 8;
                        guiGraphics.fill(msgX + 8, separatorY, msgX + messageWidth - 48, separatorY + 1, 0xFF888888);
                        lineY += element.getHeight();
                        continue;
                    }
                    
                    // 计算缩进
                    int indentX = msgX + 8 + (element.indentLevel * 16);
                    
                    // 使用元素自身的渲染方法
                    element.render(guiGraphics, this.font, indentX, lineY, messageWidth - 40);
                    
                    // 使用元素的实际高度
                    lineY += element.getHeight();
                }
            }
            
            y += totalHeight + 16; // 消息间距
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
            // 聊天区域滚动 - 使用准确的区域计算
            int totalHeight = calculateChatContentHeight();
            int chatY = MARGIN + 30;
            int chatHeight = this.height - chatY - INPUT_HEIGHT - 2 * MARGIN;
            int maxScroll = Math.max(0, totalHeight - chatHeight);
            
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

    // 辅助方法：获取文字颜色
    private int getTextColor(MarkdownElement element) {
        switch (element.getClass().getSimpleName()) {
            case "HeadingElement":
                return 0xFF2E7D32; // 深绿色标题
            case "ListElement":
                return 0xFF1976D2; // 蓝色列表项
            case "CodeBlockElement":
                return 0xFFD32F2F; // 代码红色
            default:
                return 0xFF000000; // 黑色普通文字
        }
    }
    
    // 辅助方法：获取行高
    private int getLineHeight(MarkdownElement element) {
        switch (element.getClass().getSimpleName()) {
            case "HeadingElement":
                return 24;
            case "CodeBlockElement":
                return 16;
            case "SeparatorElement":
                return 20;
            default:
                return 14;
        }
    }
} 
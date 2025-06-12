package com.swx.mcompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;

public class KeyBindings {
    public static final String KEY_CATEGORY_MCOMPANION = "key.category.mcompanion";
    public static final String KEY_OPEN_CHAT = "key.mcompanion.open_chat";

    public static final KeyMapping OPEN_CHAT_KEY = new KeyMapping(
            KEY_OPEN_CHAT,
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_MINUS, // 默认为 "-" 键
            KEY_CATEGORY_MCOMPANION
    );
} 
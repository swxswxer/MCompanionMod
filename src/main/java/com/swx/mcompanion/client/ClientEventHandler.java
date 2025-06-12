package com.swx.mcompanion.client;

import com.swx.mcompanion.client.gui.AiChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mcompanion", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        
        // 检查是否按下了我们的按键
        if (KeyBindings.OPEN_CHAT_KEY.consumeClick()) {
            // 打开AI聊天界面
            minecraft.setScreen(new AiChatScreen());
        }
    }
} 
package com.mushurelie.mcmcp.tools.chat;

import com.mushurelie.mcmcp.MinecraftMcp;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Buffer circulaire des messages de chat recus cote client, alimente par
 * ClientChatReceivedEvent (evenement client, capte tout ce qui s'affiche dans
 * le tchat : messages joueurs, sortie de commandes, messages systeme).
 */
@Mod.EventBusSubscriber(modid = MinecraftMcp.MODID, value = Side.CLIENT)
public final class ChatHistoryBuffer {

    private static final int MAX_SIZE = 500;
    private static final Deque<String> BUFFER = new ArrayDeque<>(MAX_SIZE);

    private ChatHistoryBuffer() {
    }

    @SubscribeEvent
    public static void onClientChat(ClientChatReceivedEvent event) {
        synchronized (BUFFER) {
            BUFFER.addLast(event.getMessage().getUnformattedText());
            while (BUFFER.size() > MAX_SIZE) {
                BUFFER.removeFirst();
            }
        }
    }

    /** Renvoie les n derniers messages, du plus ancien au plus recent. */
    public static List<String> lastN(int n) {
        synchronized (BUFFER) {
            List<String> all = new ArrayList<>(BUFFER);
            int fromIndex = Math.max(0, all.size() - n);
            return new ArrayList<>(all.subList(fromIndex, all.size()));
        }
    }
}

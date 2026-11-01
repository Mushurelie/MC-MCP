package com.mushurelie.mcmcp.tools.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.network.chat.Component;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Chat & commandes" : chat sortant/entrant, execution de commande avec
 * capture de sortie, actionbar, titres.
 */
public final class ChatTools {

    private ChatTools() {
    }

    public static List<Tool> createAll() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new SendChatTool());
        tools.add(new ReadChatHistoryTool());
        tools.add(new ExecuteCommandTool());
        tools.add(new SendActionbarTool());
        tools.add(new SendTitleTool());
        return tools;
    }

    private static MinecraftServer requireServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Serveur integre indisponible");
        }
        return server;
    }

    private static ServerPlayer requirePlayer(MinecraftServer server) {
        ServerPlayer player = BlockPosCodec.getSinglePlayer(server);
        if (player == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Aucun joueur en jeu (partie non chargee ?)");
        }
        return player;
    }

    // ---------------------------------------------------------------- send_chat

    static final class SendChatTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("message", JsonSchema.string("Message a envoyer dans le tchat, comme si le joueur l'avait tape"), true)
                .build();
            return new ToolDefinition("send_chat", "Envoie un message dans le tchat au nom du joueur.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                MinecraftServer server = requireServer();
                ServerPlayer player = requirePlayer(server);
                String message = params.get("message").getAsString();
                Component formatted = Component.translatable("chat.type.text", player.getDisplayName(), message);
                server.getPlayerList().broadcastSystemMessage(formatted, false);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- read_chat_history

    static final class ReadChatHistoryTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("n", JsonSchema.integer("Nombre de messages a recuperer (defaut 50)"), false)
                .build();
            return new ToolDefinition("read_chat_history",
                "Renvoie les n derniers messages de tchat vus par le client (buffer circulaire).",
                schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnClientThread(() -> {
                int n = params.has("n") ? params.get("n").getAsInt() : 50;
                List<String> messages = ChatHistoryBuffer.lastN(n);
                JsonArray array = new JsonArray();
                for (String message : messages) {
                    array.add(message);
                }
                JsonObject result = new JsonObject();
                result.add("messages", array);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- execute_command

    static final class ExecuteCommandTool implements Tool {
        /** Niveau de permission utilise : 4 (equivalent host/op), coherent avec le comportement vanilla en solo. */
        private static final int PERMISSION_LEVEL = 4;

        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("command", JsonSchema.string("Commande a executer, sans le slash initial (ex: 'time set day')"), true)
                .prop("capture", JsonSchema.bool("Si true (defaut), capture et renvoie la sortie texte de la commande"), false)
                .build();
            return new ToolDefinition("execute_command",
                "Execute une commande vanilla ou de mod avec les permissions du joueur, et renvoie sa sortie texte.",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                MinecraftServer server = requireServer();
                ServerPlayer player = requirePlayer(server);
                String command = params.get("command").getAsString();
                boolean capture = !params.has("capture") || params.get("capture").getAsBoolean();

                CapturingCommandSource capturing = new CapturingCommandSource();
                CommandSourceStack source = new CommandSourceStack(capturing, player.position(),
                    new Vec2(player.getXRot(), player.getYRot()), player.serverLevel(), PERMISSION_LEVEL,
                    player.getScoreboardName(), player.getName(), server, player);

                int executed = server.getCommands().performPrefixedCommand(source, command);

                JsonObject result = new JsonObject();
                result.addProperty("success", executed > 0);
                result.addProperty("commandsExecuted", executed);
                if (capture) {
                    JsonArray output = new JsonArray();
                    for (String line : capturing.getCapturedLines()) {
                        output.add(line);
                    }
                    result.add("output", output);
                }
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- send_actionbar

    static final class SendActionbarTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("message", JsonSchema.string("Texte a afficher dans l'actionbar (au-dessus de la barre d'objets)"), true)
                .build();
            return new ToolDefinition("send_actionbar", "Affiche un message dans l'actionbar du joueur.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                String message = params.get("message").getAsString();
                player.displayClientMessage(Component.literal(message), true);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- send_title

    static final class SendTitleTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("title", JsonSchema.string("Texte principal du titre"), true)
                .prop("subtitle", JsonSchema.string("Sous-titre optionnel"), false)
                .prop("fadeIn", JsonSchema.integer("Duree d'apparition en ticks. Defaut : 10"), false)
                .prop("stay", JsonSchema.integer("Duree d'affichage en ticks. Defaut : 70"), false)
                .prop("fadeOut", JsonSchema.integer("Duree de disparition en ticks. Defaut : 20"), false)
                .build();
            return new ToolDefinition("send_title", "Affiche un titre/sous-titre plein ecran au joueur.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                String title = params.get("title").getAsString();
                String subtitle = params.has("subtitle") ? params.get("subtitle").getAsString() : null;
                int fadeIn = params.has("fadeIn") ? params.get("fadeIn").getAsInt() : 10;
                int stay = params.has("stay") ? params.get("stay").getAsInt() : 70;
                int fadeOut = params.has("fadeOut") ? params.get("fadeOut").getAsInt() : 20;

                player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
                if (subtitle != null) {
                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
                }
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }
}

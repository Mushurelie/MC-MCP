package com.mushurelie.mcmcp.tools.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import com.mushurelie.mcmcp.util.ItemStackSerializer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Joueur" : etat, deplacement instantane (teleport), gamemode, spawn,
 * inventaire. Toujours execute sur le thread serveur (ServerPlayer est
 * l'instance autoritaire, meme en solo).
 */
public final class PlayerTools {

    /**
     * Adressage des slots (partage avec tools.craft.CraftTools) :
     * 0-8 hotbar, 9-35 inventaire principal, 36 offhand, 37 tete, 38 torse, 39 jambes, 40 pieds.
     */
    public static final int SLOT_MAIN_COUNT = 36;
    public static final int SLOT_OFFHAND = 36;
    public static final int SLOT_HEAD = 37;
    public static final int SLOT_CHEST = 38;
    public static final int SLOT_LEGS = 39;
    public static final int SLOT_FEET = 40;

    private PlayerTools() {
    }

    public static List<Tool> createAll() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new GetPlayerStateTool());
        tools.add(new TeleportTool());
        tools.add(new SetGamemodeTool());
        tools.add(new SetSpawnTool());
        tools.add(new GetInventoryTool());
        tools.add(new GiveItemTool());
        tools.add(new ClearSlotTool());
        tools.add(new EquipSlotTool());
        return tools;
    }

    private static ServerPlayer requirePlayer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = BlockPosCodec.getSinglePlayer(server);
        if (player == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Aucun joueur en jeu (partie non chargee ?)");
        }
        return player;
    }

    public static ItemStack getSlotStack(ServerPlayer player, int slot) {
        if (slot >= 0 && slot < SLOT_MAIN_COUNT) {
            return player.getInventory().items.get(slot);
        }
        switch (slot) {
            case SLOT_OFFHAND: return player.getInventory().offhand.get(0);
            case SLOT_HEAD: return player.getInventory().armor.get(3);
            case SLOT_CHEST: return player.getInventory().armor.get(2);
            case SLOT_LEGS: return player.getInventory().armor.get(1);
            case SLOT_FEET: return player.getInventory().armor.get(0);
            default:
                throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Slot invalide : " + slot);
        }
    }

    public static void setSlotStack(ServerPlayer player, int slot, ItemStack stack) {
        if (slot >= 0 && slot < SLOT_MAIN_COUNT) {
            player.getInventory().items.set(slot, stack);
        } else {
            switch (slot) {
                case SLOT_OFFHAND: player.getInventory().offhand.set(0, stack); break;
                case SLOT_HEAD: player.getInventory().armor.set(3, stack); break;
                case SLOT_CHEST: player.getInventory().armor.set(2, stack); break;
                case SLOT_LEGS: player.getInventory().armor.set(1, stack); break;
                case SLOT_FEET: player.getInventory().armor.set(0, stack); break;
                default:
                    throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Slot invalide : " + slot);
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    // ---------------------------------------------------------------- get_player_state

    static final class GetPlayerStateTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            return new ToolDefinition("get_player_state",
                "Renvoie l'etat complet du joueur : position, rotation, sante, faim, XP, gamemode, dimension, effets, slot selectionne.",
                JsonSchema.object().build(), true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer();
                JsonObject result = new JsonObject();

                JsonObject pos = new JsonObject();
                pos.addProperty("x", player.getX());
                pos.addProperty("y", player.getY());
                pos.addProperty("z", player.getZ());
                result.add("pos", pos);

                JsonObject rotation = new JsonObject();
                rotation.addProperty("yaw", player.getYRot());
                rotation.addProperty("pitch", player.getXRot());
                result.add("rotation", rotation);

                result.addProperty("health", player.getHealth());
                result.addProperty("maxHealth", player.getMaxHealth());
                result.addProperty("food", player.getFoodData().getFoodLevel());
                result.addProperty("saturation", player.getFoodData().getSaturationLevel());
                result.addProperty("xpLevel", player.experienceLevel);
                result.addProperty("xpProgress", player.experienceProgress);
                result.addProperty("gamemode", player.gameMode.getGameModeForPlayer().getName());
                result.addProperty("dimension", player.level().dimension().location().toString());
                result.addProperty("selectedSlot", player.getInventory().selected);
                result.addProperty("onGround", player.onGround());
                result.addProperty("name", player.getName().getString());

                JsonArray effects = new JsonArray();
                for (MobEffectInstance effect : player.getActiveEffects()) {
                    JsonObject e = new JsonObject();
                    e.addProperty("name", net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect()) == null
                        ? "unknown" : net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect()).toString());
                    e.addProperty("amplifier", effect.getAmplifier());
                    e.addProperty("durationTicks", effect.getDuration());
                    effects.add(e);
                }
                result.add("effects", effects);

                return result;
            });
        }
    }

    // ---------------------------------------------------------------- teleport

    static final class TeleportTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.number("Coordonnee X de destination"), true)
                .prop("y", JsonSchema.number("Coordonnee Y de destination"), true)
                .prop("z", JsonSchema.number("Coordonnee Z de destination"), true)
                .prop("yaw", JsonSchema.number("Orientation horizontale (degres). Defaut : orientation actuelle"), false)
                .prop("pitch", JsonSchema.number("Orientation verticale (degres). Defaut : orientation actuelle"), false)
                .build();
            return new ToolDefinition("teleport", "Teleporte instantanement le joueur a une position donnee (meme dimension).", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer();
                double x = params.get("x").getAsDouble();
                double y = params.get("y").getAsDouble();
                double z = params.get("z").getAsDouble();
                float yaw = params.has("yaw") ? params.get("yaw").getAsFloat() : player.getYRot();
                float pitch = params.has("pitch") ? params.get("pitch").getAsFloat() : player.getXRot();
                player.connection.teleport(x, y, z, yaw, pitch);

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- set_gamemode

    static final class SetGamemodeTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("mode", JsonSchema.stringEnum("Mode de jeu cible", "survival", "creative", "adventure", "spectator"), true)
                .build();
            return new ToolDefinition("set_gamemode", "Change le mode de jeu du joueur.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer();
                String modeName = params.get("mode").getAsString();
                GameType gameType = GameType.byName(modeName, null);
                if (gameType == null) {
                    throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Mode de jeu invalide : " + modeName);
                }
                player.setGameMode(gameType);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("gamemode", gameType.getName());
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- set_spawn

    static final class SetSpawnTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X du point de spawn"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y du point de spawn"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z du point de spawn"), true)
                .build();
            return new ToolDefinition("set_spawn", "Definit le point de reapparition personnel du joueur (equivalent d'un lit).", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer();
                BlockPos pos = BlockPosCodec.fromJson(params);
                player.setRespawnPosition(player.level().dimension(), pos, 0.0f, true, false);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- get_inventory

    static final class GetInventoryTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            return new ToolDefinition("get_inventory",
                "Renvoie l'inventaire complet du joueur : slots principaux (0-35), main droite offhand (36) et armure (37-40).",
                JsonSchema.object().build(), true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer();
                JsonArray slots = new JsonArray();
                for (int slot = 0; slot <= SLOT_FEET; slot++) {
                    ItemStack stack = getSlotStack(player, slot);
                    JsonObject slotJson = ItemStackSerializer.toJson(stack);
                    slotJson.addProperty("slot", slot);
                    slots.add(slotJson);
                }
                JsonObject result = new JsonObject();
                result.add("slots", slots);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- give_item

    static final class GiveItemTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("item", JsonSchema.string("Identifiant de registre de l'item, ex: 'minecraft:diamond'"), true)
                .prop("count", JsonSchema.integer("Quantite a donner"), true)
                .prop("nbt", JsonSchema.object("NBT optionnel a appliquer a l'item"), false)
                .build();
            return new ToolDefinition("give_item", "Donne un item au joueur (ajoute a l'inventaire, ou le fait tomber si plein).", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer();
                String item = params.get("item").getAsString();
                int count = params.get("count").getAsInt();
                JsonElement nbt = params.has("nbt") ? params.get("nbt") : null;
                ItemStack stack = ItemStackSerializer.buildStack(item, count, 0, nbt);
                ItemHandlerHelper.giveItemToPlayer(player, stack);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- clear_slot

    static final class ClearSlotTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("slot", JsonSchema.integer("Slot a vider (0-35 inventaire, 36 offhand, 37 tete, 38 torse, 39 jambes, 40 pieds)"), true)
                .build();
            return new ToolDefinition("clear_slot", "Vide un slot d'inventaire donne.", schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer();
                int slot = params.get("slot").getAsInt();
                setSlotStack(player, slot, ItemStack.EMPTY);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- equip_slot

    static final class EquipSlotTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("slot", JsonSchema.integer("Slot source (voir clear_slot pour l'adressage)"), true)
                .prop("targetSlot", JsonSchema.integer("Slot cible (ex: 37-40 pour equiper une armure, 36 pour l'offhand)"), true)
                .build();
            return new ToolDefinition("equip_slot",
                "Deplace (echange) l'item d'un slot vers un autre, typiquement pour equiper une armure ou l'offhand.",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer();
                int slot = params.get("slot").getAsInt();
                int targetSlot = params.get("targetSlot").getAsInt();
                ItemStack source = getSlotStack(player, slot);
                ItemStack target = getSlotStack(player, targetSlot);
                setSlotStack(player, targetSlot, source);
                setSlotStack(player, slot, target);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }
}

package com.mushurelie.mcmcp.tools.craft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mushurelie.mcmcp.dispatch.MainThreadDispatcher;
import com.mushurelie.mcmcp.mcp.JsonRpcException;
import com.mushurelie.mcmcp.mcp.types.JsonSchema;
import com.mushurelie.mcmcp.mcp.types.ToolDefinition;
import com.mushurelie.mcmcp.tools.Tool;
import com.mushurelie.mcmcp.tools.player.PlayerTools;
import com.mushurelie.mcmcp.util.BlockPosCodec;
import com.mushurelie.mcmcp.util.ItemStackSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Outils "Craft & conteneurs". craft_item simule la consommation des ingredients
 * en deux passes (verification puis application) pour rester transactionnel meme
 * sans grille de craft reelle.
 */
public final class CraftTools {

    private static final int MAX_LIST_RECIPES = 200;

    private CraftTools() {
    }

    public static List<Tool> createAll() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ListRecipesTool());
        tools.add(new CraftItemTool());
        tools.add(new OpenContainerTool());
        tools.add(new ReadContainerTool());
        tools.add(new TransferItemsTool());
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

    private static Container requireContainer(ServerLevel world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Pas de conteneur (Container) a cette position");
        }
        return (Container) blockEntity;
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    // ---------------------------------------------------------------- list_recipes

    static final class ListRecipesTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("resultFilter", JsonSchema.string("Sous-chaine filtrant l'item resultat (identifiant de registre)"), false)
                .prop("ingredientFilter", JsonSchema.string("Sous-chaine filtrant un des ingredients (identifiant de registre)"), false)
                .build();
            return new ToolDefinition("list_recipes",
                "Liste les recettes connues (tous types : craft, four, machines de mods...), filtrables par resultat et/ou ingredient (max "
                    + MAX_LIST_RECIPES + " resultats).",
                schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                MinecraftServer server = requireServer();
                String resultFilter = params.has("resultFilter") ? params.get("resultFilter").getAsString().toLowerCase() : null;
                String ingredientFilter = params.has("ingredientFilter") ? params.get("ingredientFilter").getAsString().toLowerCase() : null;

                JsonArray array = new JsonArray();
                for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
                    if (array.size() >= MAX_LIST_RECIPES) {
                        break;
                    }
                    ItemStack output = recipe.getResultItem(server.registryAccess());
                    if (output.isEmpty()) {
                        continue;
                    }
                    if (resultFilter != null && !itemId(output).toLowerCase().contains(resultFilter)) {
                        continue;
                    }
                    if (ingredientFilter != null && !matchesIngredient(recipe, ingredientFilter)) {
                        continue;
                    }
                    JsonObject json = new JsonObject();
                    json.addProperty("recipeId", recipe.getId().toString());
                    ResourceLocation typeKey = ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType());
                    json.addProperty("type", typeKey == null ? "unknown" : typeKey.toString());
                    json.add("result", ItemStackSerializer.toJson(output));
                    array.add(json);
                }
                JsonObject result = new JsonObject();
                result.add("recipes", array);
                return result;
            });
        }

        private boolean matchesIngredient(Recipe<?> recipe, String filter) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack candidate : ingredient.getItems()) {
                    if (itemId(candidate).toLowerCase().contains(filter)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    // ---------------------------------------------------------------- craft_item

    static final class CraftItemTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("recipeId", JsonSchema.string("Identifiant de la recette (voir list_recipes)"), true)
                .prop("count", JsonSchema.integer("Nombre de fois a fabriquer la recette"), true)
                .build();
            return new ToolDefinition("craft_item",
                "Fabrique un item via une recette connue, en consommant les ingredients de l'inventaire du joueur.",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                MinecraftServer server = requireServer();
                ServerPlayer player = requirePlayer(server);
                String recipeId = params.get("recipeId").getAsString();
                int count = params.get("count").getAsInt();

                Optional<? extends Recipe<?>> recipe = server.getRecipeManager().byKey(new ResourceLocation(recipeId));
                if (recipe.isEmpty()) {
                    throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Recette inconnue : " + recipeId);
                }

                List<Ingredient> required = new ArrayList<>();
                for (Ingredient ingredient : recipe.get().getIngredients()) {
                    if (!ingredient.isEmpty()) {
                        required.add(ingredient);
                    }
                }

                NonNullList<ItemStack> inventory = player.getInventory().items;

                // Passe 1 : simulation sur une copie profonde, sans toucher a l'inventaire reel.
                List<ItemStack> simulation = new ArrayList<>();
                for (ItemStack stack : inventory) {
                    simulation.add(stack.copy());
                }
                for (int craft = 0; craft < count; craft++) {
                    for (Ingredient ingredient : required) {
                        int index = findMatchingIndex(simulation, ingredient);
                        if (index < 0) {
                            throw new JsonRpcException(JsonRpcException.SERVER_ERROR,
                                "Ingredients insuffisants pour fabriquer " + count + "x " + recipeId);
                        }
                        decrementAt(simulation, index);
                    }
                }

                // Passe 2 : simulation validee, on applique reellement sur l'inventaire du joueur.
                for (int craft = 0; craft < count; craft++) {
                    for (Ingredient ingredient : required) {
                        decrementAt(inventory, findMatchingIndex(inventory, ingredient));
                    }
                }

                ItemStack output = recipe.get().getResultItem(server.registryAccess()).copy();
                output.setCount(output.getCount() * count);
                ItemHandlerHelper.giveItemToPlayer(player, output);
                player.inventoryMenu.broadcastChanges();

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("crafted", count);
                return result;
            });
        }

        private static int findMatchingIndex(List<ItemStack> inventory, Ingredient ingredient) {
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.get(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    return i;
                }
            }
            return -1;
        }

        private static void decrementAt(List<ItemStack> inventory, int index) {
            ItemStack stack = inventory.get(index);
            stack.shrink(1);
            if (stack.isEmpty()) {
                inventory.set(index, ItemStack.EMPTY);
            }
        }
    }

    // ---------------------------------------------------------------- open_container

    static final class OpenContainerTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X du conteneur"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y du conteneur"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z du conteneur"), true)
                .build();
            return new ToolDefinition("open_container",
                "Signale l'ouverture d'un conteneur (anime le couvercle pour les coffres). Purement cosmetique : "
                    + "read_container/transfer_items fonctionnent sans appel prealable a open_container.",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof ChestBlockEntity) {
                    world.blockEvent(pos, world.getBlockState(pos).getBlock(), 1, 1);
                }
                JsonObject result = new JsonObject();
                result.addProperty("success", blockEntity instanceof Container);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- read_container

    static final class ReadContainerTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("x", JsonSchema.integer("Coordonnee X du conteneur"), true)
                .prop("y", JsonSchema.integer("Coordonnee Y du conteneur"), true)
                .prop("z", JsonSchema.integer("Coordonnee Z du conteneur"), true)
                .build();
            return new ToolDefinition("read_container", "Lit le contenu complet d'un conteneur (coffre, fourneau, tonneau, etc.).", schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerLevel world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                Container container = requireContainer(world, pos);

                JsonArray slots = new JsonArray();
                for (int i = 0; i < container.getContainerSize(); i++) {
                    JsonObject slotJson = ItemStackSerializer.toJson(container.getItem(i));
                    slotJson.addProperty("slot", i);
                    slots.add(slotJson);
                }
                JsonObject result = new JsonObject();
                result.add("slots", slots);
                return result;
            });
        }
    }

    // ---------------------------------------------------------------- transfer_items

    static final class TransferItemsTool implements Tool {
        @Override
        public ToolDefinition getDefinition() {
            JsonObject schema = JsonSchema.object()
                .prop("fromSlot", JsonSchema.integer("Slot source (adressage inventaire joueur, voir clear_slot)"), true)
                .prop("toSlot", JsonSchema.integer("Slot destination (meme adressage)"), true)
                .prop("count", JsonSchema.integer("Nombre d'items a deplacer"), true)
                .build();
            return new ToolDefinition("transfer_items",
                "Deplace des items entre deux slots de l'inventaire du joueur (fusion si compatible, sinon echange).",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                ServerPlayer player = requirePlayer(requireServer());
                int fromSlot = params.get("fromSlot").getAsInt();
                int toSlot = params.get("toSlot").getAsInt();
                int count = params.get("count").getAsInt();

                ItemStack source = PlayerTools.getSlotStack(player, fromSlot);
                if (source.isEmpty()) {
                    throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Slot source vide : " + fromSlot);
                }
                ItemStack target = PlayerTools.getSlotStack(player, toSlot);
                int moveCount = Math.min(count, source.getCount());

                if (target.isEmpty()) {
                    ItemStack moved = source.split(moveCount);
                    PlayerTools.setSlotStack(player, toSlot, moved);
                    PlayerTools.setSlotStack(player, fromSlot, source.isEmpty() ? ItemStack.EMPTY : source);
                } else if (ItemStack.isSameItemSameTags(source, target) && target.getCount() < target.getMaxStackSize()) {
                    int canAdd = Math.min(moveCount, target.getMaxStackSize() - target.getCount());
                    target.grow(canAdd);
                    source.shrink(canAdd);
                    PlayerTools.setSlotStack(player, fromSlot, source.isEmpty() ? ItemStack.EMPTY : source);
                    PlayerTools.setSlotStack(player, toSlot, target);
                } else {
                    PlayerTools.setSlotStack(player, fromSlot, target);
                    PlayerTools.setSlotStack(player, toSlot, source);
                }

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                return result;
            });
        }
    }
}

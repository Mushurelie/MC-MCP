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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
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
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Serveur integre indisponible");
        }
        return server;
    }

    private static EntityPlayerMP requirePlayer(MinecraftServer server) {
        EntityPlayerMP player = BlockPosCodec.getSinglePlayer(server);
        if (player == null) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Aucun joueur en jeu (partie non chargee ?)");
        }
        return player;
    }

    private static IInventory requireInventory(WorldServer world, BlockPos pos) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IInventory)) {
            throw new JsonRpcException(JsonRpcException.SERVER_ERROR, "Pas de conteneur (IInventory) a cette position");
        }
        return (IInventory) tileEntity;
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
                "Liste les recettes de craft connues, filtrables par resultat et/ou ingredient (max " + MAX_LIST_RECIPES + " resultats).",
                schema, true);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                String resultFilter = params.has("resultFilter") ? params.get("resultFilter").getAsString().toLowerCase() : null;
                String ingredientFilter = params.has("ingredientFilter") ? params.get("ingredientFilter").getAsString().toLowerCase() : null;

                JsonArray array = new JsonArray();
                for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
                    if (array.size() >= MAX_LIST_RECIPES) {
                        break;
                    }
                    ItemStack output = recipe.getRecipeOutput();
                    if (output.isEmpty()) {
                        continue;
                    }
                    String outputId = output.getItem().getRegistryName() == null ? "" : output.getItem().getRegistryName().toString();
                    if (resultFilter != null && !outputId.toLowerCase().contains(resultFilter)) {
                        continue;
                    }
                    if (ingredientFilter != null && !matchesIngredient(recipe, ingredientFilter)) {
                        continue;
                    }
                    JsonObject json = new JsonObject();
                    json.addProperty("recipeId", recipe.getRegistryName() == null ? "unknown" : recipe.getRegistryName().toString());
                    json.add("result", ItemStackSerializer.toJson(output));
                    array.add(json);
                }
                JsonObject result = new JsonObject();
                result.add("recipes", array);
                return result;
            });
        }

        private boolean matchesIngredient(IRecipe recipe, String filter) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack candidate : ingredient.getMatchingStacks()) {
                    String id = candidate.getItem().getRegistryName() == null ? "" : candidate.getItem().getRegistryName().toString();
                    if (id.toLowerCase().contains(filter)) {
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
                .prop("recipeId", JsonSchema.string("Identifiant de registre de la recette (voir list_recipes)"), true)
                .prop("count", JsonSchema.integer("Nombre de fois a fabriquer la recette"), true)
                .build();
            return new ToolDefinition("craft_item",
                "Fabrique un item via une recette connue, en consommant les ingredients de l'inventaire du joueur.",
                schema, false);
        }

        @Override
        public CompletableFuture<JsonElement> execute(JsonObject params) {
            return MainThreadDispatcher.runOnServerThread(() -> {
                EntityPlayerMP player = requirePlayer(requireServer());
                String recipeId = params.get("recipeId").getAsString();
                int count = params.get("count").getAsInt();

                IRecipe recipe = ForgeRegistries.RECIPES.getValue(new net.minecraft.util.ResourceLocation(recipeId));
                if (recipe == null) {
                    throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Recette inconnue : " + recipeId);
                }

                List<Ingredient> required = new ArrayList<>();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient != Ingredient.EMPTY && ingredient.getMatchingStacks().length > 0) {
                        required.add(ingredient);
                    }
                }

                // Passe 1 : simulation sur une copie profonde, sans toucher a l'inventaire reel.
                List<ItemStack> simulation = new ArrayList<>();
                for (ItemStack stack : player.inventory.mainInventory) {
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
                        int index = findMatchingIndex(player.inventory.mainInventory, ingredient);
                        decrementAt(player.inventory.mainInventory, index);
                    }
                }

                ItemStack output = recipe.getRecipeOutput().copy();
                output.setCount(output.getCount() * count);
                ItemHandlerHelper.giveItemToPlayer(player, output);
                player.inventoryContainer.detectAndSendChanges();

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("crafted", count);
                return result;
            });
        }

        private static int findMatchingIndex(List<ItemStack> inventory, Ingredient ingredient) {
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.get(i);
                if (!stack.isEmpty() && ingredient.apply(stack)) {
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
                WorldServer world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                TileEntity tileEntity = world.getTileEntity(pos);
                if (tileEntity instanceof TileEntityChest) {
                    world.addBlockEvent(pos, world.getBlockState(pos).getBlock(), 1, 1);
                }
                JsonObject result = new JsonObject();
                result.addProperty("success", tileEntity instanceof IInventory);
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
                WorldServer world = BlockPosCodec.resolveWorld(params);
                BlockPos pos = BlockPosCodec.fromJson(params);
                IInventory inventory = requireInventory(world, pos);

                JsonArray slots = new JsonArray();
                for (int i = 0; i < inventory.getSizeInventory(); i++) {
                    JsonObject slotJson = ItemStackSerializer.toJson(inventory.getStackInSlot(i));
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
                EntityPlayerMP player = requirePlayer(requireServer());
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
                    ItemStack moved = source.splitStack(moveCount);
                    PlayerTools.setSlotStack(player, toSlot, moved);
                    PlayerTools.setSlotStack(player, fromSlot, source.isEmpty() ? ItemStack.EMPTY : source);
                } else if (ItemStack.areItemsEqual(source, target) && ItemStack.areItemStackTagsEqual(source, target)
                    && target.getCount() < target.getMaxStackSize()) {
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

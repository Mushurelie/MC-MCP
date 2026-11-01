package com.mushurelie.mcmcp.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Serialisation / deserialisation d'ItemStack en JSON pour l'API MCP.
 * Format : {"item": "modid:name", "count": int, "damage": int, "nbt": {...}}
 */
public final class ItemStackSerializer {

    private ItemStackSerializer() {
    }

    public static JsonObject toJson(ItemStack stack) {
        JsonObject json = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            json.addProperty("empty", true);
            return json;
        }
        json.addProperty("empty", false);
        json.addProperty("item", stack.getItem().builtInRegistryHolder().key().location() == null
            ? "unknown"
            : stack.getItem().builtInRegistryHolder().key().location().toString());
        json.addProperty("displayName", stack.getDisplayName().getString());
        json.addProperty("count", stack.getCount());
        json.addProperty("damage", stack.getDamageValue());
        json.addProperty("maxStackSize", stack.getMaxStackSize());
        if (stack.hasTag()) {
            json.add("nbt", NbtToJson.compoundToJson(stack.getTag()));
        }
        return json;
    }

    /** Recherche un Item par son identifiant de registre (ex: "minecraft:diamond"). */
    public static Item findItem(String itemId) {
        ResourceLocation location = new ResourceLocation(itemId);
        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item == null) {
            throw new IllegalArgumentException("Item inconnu : " + itemId);
        }
        return item;
    }

    /** Construit un ItemStack a partir des parametres JSON-RPC classiques (item, count, damage, nbt). */
    public static ItemStack buildStack(String itemId, int count, int damage, JsonElement nbt) {
        Item item = findItem(itemId);
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (damage > 0) {
            stack.setDamageValue(damage);
        }
        if (nbt != null && !nbt.isJsonNull()) {
            CompoundTag compound = NbtToJson.jsonToCompound(nbt);
            stack.setTag(compound);
        }
        return stack;
    }
}

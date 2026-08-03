package com.mushurelie.mcmcp.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

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
        json.addProperty("item", stack.getItem().getRegistryName() == null
            ? "unknown"
            : stack.getItem().getRegistryName().toString());
        json.addProperty("displayName", stack.getDisplayName());
        json.addProperty("count", stack.getCount());
        json.addProperty("damage", stack.getItemDamage());
        json.addProperty("maxStackSize", stack.getMaxStackSize());
        if (stack.hasTagCompound()) {
            json.add("nbt", NbtToJson.compoundToJson(stack.getTagCompound()));
        }
        return json;
    }

    /** Recherche un Item par son identifiant de registre (ex: "minecraft:diamond"). */
    public static Item findItem(String itemId) {
        net.minecraft.util.ResourceLocation location = new net.minecraft.util.ResourceLocation(itemId);
        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item == null) {
            throw new IllegalArgumentException("Item inconnu : " + itemId);
        }
        return item;
    }

    /** Construit un ItemStack a partir des parametres JSON-RPC classiques (item, count, damage, nbt). */
    public static ItemStack buildStack(String itemId, int count, int damage, JsonElement nbt) {
        Item item = findItem(itemId);
        ItemStack stack = new ItemStack(item, Math.max(1, count), damage);
        if (nbt != null && !nbt.isJsonNull()) {
            NBTTagCompound compound = NbtToJson.jsonToCompound(nbt);
            stack.setTagCompound(compound);
        }
        return stack;
    }
}

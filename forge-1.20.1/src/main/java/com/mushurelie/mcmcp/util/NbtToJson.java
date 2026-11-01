package com.mushurelie.mcmcp.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;

/**
 * Convertit du NBT Minecraft en JSON (Gson) pour l'exposer via JSON-RPC.
 * Tronque les gros tableaux/chaines pour eviter de saturer les reponses.
 */
public final class NbtToJson {

    private static final int MAX_STRING_LENGTH = 512;
    private static final int MAX_ARRAY_ELEMENTS = 64;

    private NbtToJson() {
    }

    public static JsonElement convert(Tag nbt) {
        if (nbt == null) {
            return JsonNull.INSTANCE;
        }
        if (nbt instanceof CompoundTag) {
            return convertCompound((CompoundTag) nbt);
        }
        if (nbt instanceof ListTag) {
            return convertList((ListTag) nbt);
        }
        if (nbt instanceof StringTag) {
            String s = nbt.getAsString();
            return new JsonPrimitive(truncate(s));
        }
        if (nbt instanceof ByteTag) {
            return new JsonPrimitive(((ByteTag) nbt).getAsByte());
        }
        if (nbt instanceof ShortTag) {
            return new JsonPrimitive(((ShortTag) nbt).getAsShort());
        }
        if (nbt instanceof IntTag) {
            return new JsonPrimitive(((IntTag) nbt).getAsInt());
        }
        if (nbt instanceof LongTag) {
            return new JsonPrimitive(((LongTag) nbt).getAsLong());
        }
        if (nbt instanceof FloatTag) {
            return new JsonPrimitive(((FloatTag) nbt).getAsFloat());
        }
        if (nbt instanceof DoubleTag) {
            return new JsonPrimitive(((DoubleTag) nbt).getAsDouble());
        }
        if (nbt instanceof ByteArrayTag) {
            byte[] arr = ((ByteArrayTag) nbt).getAsByteArray();
            JsonArray json = new JsonArray();
            for (int i = 0; i < Math.min(arr.length, MAX_ARRAY_ELEMENTS); i++) {
                json.add(arr[i]);
            }
            return json;
        }
        if (nbt instanceof IntArrayTag) {
            int[] arr = ((IntArrayTag) nbt).getAsIntArray();
            JsonArray json = new JsonArray();
            for (int i = 0; i < Math.min(arr.length, MAX_ARRAY_ELEMENTS); i++) {
                json.add(arr[i]);
            }
            return json;
        }
        // EndNBT ou type inconnu
        return JsonNull.INSTANCE;
    }

    private static JsonObject convertCompound(CompoundTag compound) {
        JsonObject json = new JsonObject();
        for (String key : compound.getAllKeys()) {
            json.add(key, convert(compound.get(key)));
        }
        return json;
    }

    private static JsonArray convertList(ListTag list) {
        JsonArray json = new JsonArray();
        int count = Math.min(list.size(), MAX_ARRAY_ELEMENTS);
        for (int i = 0; i < count; i++) {
            json.add(convert(list.get(i)));
        }
        return json;
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_STRING_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_STRING_LENGTH) + "...(tronque)";
    }

    /**
     * Conversion inverse (best-effort) : JSON -> NBT, utilisee par les outils d'ecriture
     * qui acceptent un parametre "nbt" optionnel (ex : give_item, set_block, spawn_entity).
     * Les nombres JSON sont mappes vers DoubleTag/IntTag selon qu'ils sont entiers ou non.
     */
    public static Tag toNbt(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            CompoundTag compound = new CompoundTag();
            JsonObject obj = element.getAsJsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                Tag value = toNbt(entry.getValue());
                if (value != null) {
                    compound.put(entry.getKey(), value);
                }
            }
            return compound;
        }
        if (element.isJsonArray()) {
            ListTag list = new ListTag();
            for (JsonElement e : element.getAsJsonArray()) {
                Tag value = toNbt(e);
                if (value != null) {
                    list.add(value);
                }
            }
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return ByteTag.valueOf((byte) (primitive.getAsBoolean() ? 1 : 0));
        }
        if (primitive.isNumber()) {
            String raw = primitive.getAsString();
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return DoubleTag.valueOf(primitive.getAsDouble());
            }
            long l = primitive.getAsLong();
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return IntTag.valueOf((int) l);
            }
            return LongTag.valueOf(l);
        }
        return StringTag.valueOf(primitive.getAsString());
    }

    /** Convertit un compound NBT complet en compound JSON (racine toujours un objet). */
    public static JsonObject compoundToJson(CompoundTag compound) {
        if (compound == null) {
            return new JsonObject();
        }
        return convertCompound(compound);
    }

    /** Parse un objet JSON en CompoundTag (racine). Retourne un compound vide si null/non-objet. */
    public static CompoundTag jsonToCompound(JsonElement element) {
        Tag nbt = toNbt(element);
        if (nbt instanceof CompoundTag) {
            return (CompoundTag) nbt;
        }
        return new CompoundTag();
    }
}

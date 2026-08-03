package com.mushurelie.mcmcp.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

/**
 * Convertit du NBT Minecraft en JSON (Gson) pour l'exposer via JSON-RPC.
 * Tronque les gros tableaux/chaines pour eviter de saturer les reponses.
 */
public final class NbtToJson {

    private static final int MAX_STRING_LENGTH = 512;
    private static final int MAX_ARRAY_ELEMENTS = 64;

    private NbtToJson() {
    }

    public static JsonElement convert(NBTBase nbt) {
        if (nbt == null) {
            return JsonNull.INSTANCE;
        }
        if (nbt instanceof NBTTagCompound) {
            return convertCompound((NBTTagCompound) nbt);
        }
        if (nbt instanceof NBTTagList) {
            return convertList((NBTTagList) nbt);
        }
        if (nbt instanceof NBTTagString) {
            String s = ((NBTTagString) nbt).getString();
            return new JsonPrimitive(truncate(s));
        }
        if (nbt instanceof NBTTagByte) {
            return new JsonPrimitive(((NBTTagByte) nbt).getByte());
        }
        if (nbt instanceof NBTTagShort) {
            return new JsonPrimitive(((NBTTagShort) nbt).getShort());
        }
        if (nbt instanceof NBTTagInt) {
            return new JsonPrimitive(((NBTTagInt) nbt).getInt());
        }
        if (nbt instanceof NBTTagLong) {
            return new JsonPrimitive(((NBTTagLong) nbt).getLong());
        }
        if (nbt instanceof NBTTagFloat) {
            return new JsonPrimitive(((NBTTagFloat) nbt).getFloat());
        }
        if (nbt instanceof NBTTagDouble) {
            return new JsonPrimitive(((NBTTagDouble) nbt).getDouble());
        }
        if (nbt instanceof NBTTagByteArray) {
            byte[] arr = ((NBTTagByteArray) nbt).getByteArray();
            JsonArray json = new JsonArray();
            for (int i = 0; i < Math.min(arr.length, MAX_ARRAY_ELEMENTS); i++) {
                json.add(arr[i]);
            }
            return json;
        }
        if (nbt instanceof NBTTagIntArray) {
            int[] arr = ((NBTTagIntArray) nbt).getIntArray();
            JsonArray json = new JsonArray();
            for (int i = 0; i < Math.min(arr.length, MAX_ARRAY_ELEMENTS); i++) {
                json.add(arr[i]);
            }
            return json;
        }
        // NBTTagEnd ou type inconnu
        return JsonNull.INSTANCE;
    }

    private static JsonObject convertCompound(NBTTagCompound compound) {
        JsonObject json = new JsonObject();
        for (String key : compound.getKeySet()) {
            json.add(key, convert(compound.getTag(key)));
        }
        return json;
    }

    private static JsonArray convertList(NBTTagList list) {
        JsonArray json = new JsonArray();
        int count = Math.min(list.tagCount(), MAX_ARRAY_ELEMENTS);
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
     * Les nombres JSON sont mappes vers NBTTagDouble/NBTTagInt selon qu'ils sont entiers ou non.
     */
    public static NBTBase toNbt(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            NBTTagCompound compound = new NBTTagCompound();
            JsonObject obj = element.getAsJsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                NBTBase value = toNbt(entry.getValue());
                if (value != null) {
                    compound.setTag(entry.getKey(), value);
                }
            }
            return compound;
        }
        if (element.isJsonArray()) {
            NBTTagList list = new NBTTagList();
            for (JsonElement e : element.getAsJsonArray()) {
                NBTBase value = toNbt(e);
                if (value != null) {
                    list.appendTag(value);
                }
            }
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return new NBTTagByte((byte) (primitive.getAsBoolean() ? 1 : 0));
        }
        if (primitive.isNumber()) {
            String raw = primitive.getAsString();
            if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                return new NBTTagDouble(primitive.getAsDouble());
            }
            long l = primitive.getAsLong();
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return new NBTTagInt((int) l);
            }
            return new NBTTagLong(l);
        }
        return new NBTTagString(primitive.getAsString());
    }

    /** Convertit un compound NBT complet en compound JSON (racine toujours un objet). */
    public static JsonObject compoundToJson(NBTTagCompound compound) {
        if (compound == null) {
            return new JsonObject();
        }
        return convertCompound(compound);
    }

    /** Parse un objet JSON en NBTTagCompound (racine). Retourne un compound vide si null/non-objet. */
    public static NBTTagCompound jsonToCompound(JsonElement element) {
        NBTBase nbt = toNbt(element);
        if (nbt instanceof NBTTagCompound) {
            return (NBTTagCompound) nbt;
        }
        return new NBTTagCompound();
    }
}

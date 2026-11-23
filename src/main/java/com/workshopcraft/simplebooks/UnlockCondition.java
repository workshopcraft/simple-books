package com.workshopcraft.simplebooks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** One item, tag or advancement that can unlock a gated page, step or link. */
public record UnlockCondition(Kind kind, Identifier id) {
    public static final int MAX = 1024;
    private static final int KEY_MAX = 256;

    public enum Kind { ITEM, TAG, ADVANCEMENT }

    public String key() {
        return kind.name().toLowerCase() + ":" + id;
    }

    @Override
    public String toString() {
        return key();
    }

    public static @Nullable UnlockCondition parseKey(String key) {
        if (key == null || key.length() > KEY_MAX) return null;
        int cut = key.indexOf(':');
        if (cut <= 0 || cut == key.length() - 1) return null;
        String prefix = key.substring(0, cut), rest = key.substring(cut + 1);
        Kind kind = switch (prefix) {
            case "item" -> Kind.ITEM;
            case "tag" -> Kind.TAG;
            case "advancement" -> Kind.ADVANCEMENT;
            default -> null;
        };
        if (kind == null) return null;
        Identifier id = Identifier.tryParse(rest);
        return id == null ? null : new UnlockCondition(kind, id);
    }

    /** A grant argument: #tag, tag:id, item:id, advancement:id, or a bare item/advancement id. */
    public static @Nullable UnlockCondition parseGrant(String raw, boolean itemExists, boolean advancementExists) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.startsWith("tag:#")) s = "tag:" + s.substring(5);
        if (s.startsWith("#")) return parseKey("tag:" + s.substring(1));
        UnlockCondition named = parseKey(s);
        if (named != null) return named;
        Identifier id = Identifier.tryParse(s);
        if (id == null) return null;
        if (itemExists) return new UnlockCondition(Kind.ITEM, id);
        if (advancementExists) return new UnlockCondition(Kind.ADVANCEMENT, id);
        return new UnlockCondition(Kind.ITEM, id);
    }

    public static List<UnlockCondition> parse(JsonElement e, String ptr, @Nullable Consumer<String> error) {
        return UnlockRule.parse(e, ptr, error, null).leaves();
    }

    public static List<UnlockCondition> parseQuiet(JsonElement e) {
        return parse(e, "", null);
    }

    static @Nullable UnlockCondition one(JsonElement e, String ptr, @Nullable Consumer<String> error) {
        if (e == null || !e.isJsonObject()) {
            fail(error, ptr, "must be an object with item, tag or advancement");
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        int n = (o.has("item") ? 1 : 0) + (o.has("tag") ? 1 : 0) + (o.has("advancement") ? 1 : 0);
        for (String k : o.keySet())
            if (!k.equals("item") && !k.equals("tag") && !k.equals("advancement"))
                fail(error, ptr + "/" + k, "unknown field");
        if (n != 1) {
            fail(error, ptr, "must have exactly one of item, tag or advancement");
            return null;
        }
        Kind kind = o.has("item") ? Kind.ITEM : o.has("tag") ? Kind.TAG : Kind.ADVANCEMENT;
        String field = kind.name().toLowerCase();
        JsonElement v = o.get(field);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) {
            fail(error, ptr + "/" + field, "must be an id");
            return null;
        }
        String raw = v.getAsString();
        if (kind == Kind.TAG && raw.startsWith("#")) raw = raw.substring(1);
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            fail(error, ptr + "/" + field, "bad id");
            return null;
        }
        return new UnlockCondition(kind, id);
    }

    static void fail(@Nullable Consumer<String> error, String ptr, String msg) {
        if (error != null) error.accept((ptr == null || ptr.isEmpty() ? "unlock_on" : ptr) + ": " + msg);
    }

    /** True when a locked field is missing or is show or hide. */
    public static boolean checkLocked(JsonElement e, String ptr, Consumer<String> error) {
        if (e == null || e.isJsonNull()) return true;
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            error.accept(ptr + ": must be show or hide");
            return false;
        }
        String v = e.getAsString();
        if (!v.equals("show") && !v.equals("hide")) {
            error.accept(ptr + ": must be show or hide");
            return false;
        }
        return true;
    }
}

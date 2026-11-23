package com.workshopcraft.simplebooks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** An unlock_on value: one condition, any of a list, or all of a group. */
public final class UnlockRule {
    public enum Mode { ANY, ALL, LEAF }

    private final Mode mode;
    private final @Nullable UnlockCondition leaf;
    private final List<UnlockRule> children;

    private UnlockRule(Mode mode, @Nullable UnlockCondition leaf, List<UnlockRule> children) {
        this.mode = mode;
        this.leaf = leaf;
        this.children = children;
    }

    public Mode mode() {
        return mode;
    }

    public @Nullable UnlockCondition leaf() {
        return leaf;
    }

    public List<UnlockRule> children() {
        return children;
    }

    public static UnlockRule any(List<UnlockRule> children) {
        return new UnlockRule(Mode.ANY, null, List.copyOf(children));
    }

    public static UnlockRule all(List<UnlockRule> children) {
        return new UnlockRule(Mode.ALL, null, List.copyOf(children));
    }

    public static UnlockRule of(UnlockCondition c) {
        return new UnlockRule(Mode.LEAF, c, List.of());
    }

    public boolean met(Set<String> have) {
        return switch (mode) {
            case LEAF -> leaf != null && have.contains(leaf.key());
            case ANY -> {
                if (children.isEmpty()) yield true;
                for (UnlockRule r : children) if (r.met(have)) yield true;
                yield false;
            }
            case ALL -> {
                if (children.isEmpty()) yield false;
                for (UnlockRule r : children) if (!r.met(have)) yield false;
                yield true;
            }
        };
    }

    public List<UnlockCondition> leaves() {
        List<UnlockCondition> out = new ArrayList<>();
        collect(out);
        return out;
    }

    private void collect(List<UnlockCondition> out) {
        if (mode == Mode.LEAF) {
            if (leaf != null) out.add(leaf);
            return;
        }
        for (UnlockRule r : children) r.collect(out);
    }

    public List<UnlockCondition> missing(Set<String> have) {
        List<UnlockCondition> out = new ArrayList<>();
        addMissing(have, out);
        return out;
    }

    private void addMissing(Set<String> have, List<UnlockCondition> out) {
        if (mode == Mode.LEAF) {
            if (leaf != null && !have.contains(leaf.key())) out.add(leaf);
            return;
        }
        if (mode == Mode.ANY && met(have)) return;
        for (UnlockRule r : children) r.addMissing(have, out);
    }

    public static UnlockRule parseQuiet(JsonElement e) {
        return parse(e, "", null, null);
    }

    public static UnlockRule parse(JsonElement e, String ptr, @Nullable Consumer<String> error, @Nullable Consumer<String> warn) {
        if (e == null || e.isJsonNull()) {
            UnlockCondition.fail(error, ptr, "must be a condition or a list of them");
            return any(List.of());
        }
        if (e.isJsonArray()) return group(e.getAsJsonArray(), ptr, error, warn, false);
        if (!e.isJsonObject()) {
            UnlockCondition.fail(error, ptr, "must be a condition or a list of them");
            return any(List.of());
        }
        JsonObject o = e.getAsJsonObject();
        boolean hasAll = o.has("all"), hasAny = o.has("any");
        boolean hasLeaf = o.has("item") || o.has("tag") || o.has("advancement");
        if (hasAll || hasAny) {
            if ((hasAll && hasAny) || hasLeaf) {
                UnlockCondition.fail(error, ptr, "must be all, any, or exactly one of item, tag or advancement");
                return any(List.of());
            }
            for (String k : o.keySet())
                if (!k.equals("all") && !k.equals("any"))
                    UnlockCondition.fail(error, ptr + "/" + k, "unknown field");
            String field = hasAll ? "all" : "any";
            JsonElement v = o.get(field);
            if (v == null || !v.isJsonArray()) {
                UnlockCondition.fail(error, ptr + "/" + field, "must be a list of conditions");
                return hasAll ? all(List.of()) : any(List.of());
            }
            return group(v.getAsJsonArray(), ptr + "/" + field, error, warn, hasAll);
        }
        UnlockCondition c = UnlockCondition.one(o, ptr, error);
        return c == null ? any(List.of()) : of(c);
    }

    private static UnlockRule group(JsonArray a, String ptr, @Nullable Consumer<String> error,
                                    @Nullable Consumer<String> warn, boolean requireAll) {
        if (a.isEmpty()) {
            if (requireAll) {
                if (warn != null) warn.accept((ptr == null || ptr.isEmpty() ? "unlock_on/all" : ptr) + ": must have at least one condition");
            } else UnlockCondition.fail(error, ptr, "must have at least one condition");
        }
        List<UnlockRule> kids = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            UnlockRule r = parse(a.get(i), ptr + "/" + i, error, warn);
            if (r.mode == Mode.LEAF && r.leaf == null) continue;
            if (r.mode == Mode.ANY && r.children.isEmpty() && r.leaf == null) continue;
            kids.add(r);
        }
        return requireAll ? all(kids) : any(kids);
    }
}

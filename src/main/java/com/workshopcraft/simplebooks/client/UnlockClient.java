package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.UnlockCondition;
import com.workshopcraft.simplebooks.UnlockHints;
import com.workshopcraft.simplebooks.UnlockWatchPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client unlock state. With no Simple Books server, everything is treated as unlocked. */
public final class UnlockClient {
    private static boolean server, enabled = true;
    private static final Set<String> have = new LinkedHashSet<>();
    private static int stamp;
    private static final Map<Book, View> views = new IdentityHashMap<>();

    private UnlockClient() {}

    private record View(int stamp, Book book) {}

    public static int stamp() {
        return stamp;
    }

    public static boolean showAll() {
        return !server || !enabled;
    }

    public static void clear() {
        if (!server && have.isEmpty() && enabled) return;
        server = false;
        enabled = true;
        have.clear();
        views.clear();
        stamp++;
    }

    public static void accept(boolean on, List<String> keys) {
        server = true;
        enabled = on;
        have.clear();
        have.addAll(keys);
        views.clear();
        stamp++;
    }

    /** Apply a local /simplebooks unlocks grant or reset so the book is already up to date when it reopens. */
    public static void predict(String action, String player, String target) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!player.equalsIgnoreCase(mc.player.getScoreboardName())) return;
        if ("reset".equals(action)) {
            if (have.isEmpty()) return;
            have.clear();
            views.clear();
            stamp++;
            return;
        }
        if (!"grant".equals(action) || target == null || target.isBlank()) return;
        String raw = target.trim();
        Identifier maybe = Identifier.tryParse(raw.startsWith("advancement:") ? raw.substring("advancement:".length()) : raw);
        boolean item = Identifier.tryParse(raw) != null && BuiltInRegistries.ITEM.containsKey(Identifier.tryParse(raw));
        boolean adv = maybe != null;
        UnlockCondition c = UnlockCondition.parseGrant(raw, item, adv);
        if (c != null && have.add(c.key())) {
            views.clear();
            stamp++;
        }
    }

    public static void sendWatch() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return;
        Set<String> keys = new LinkedHashSet<>();
        for (Book b : BookRegistry.rawBooks()) collect(b, keys);
        List<String> list = new ArrayList<>(keys);
        if (list.size() > UnlockCondition.MAX) list = list.subList(0, UnlockCondition.MAX);
        SimpleBooks.toServerWatch.accept(new UnlockWatchPacket(list));
        BookRegistry.flushTagWarnings();
    }

    private static void collect(Book book, Set<String> keys) {
        gather(book.json(), keys);
        if (book.json().has("sections"))
            for (JsonElement e : book.json().getAsJsonArray("sections")) gather(e, keys);
        for (Book.Page p : book.pages()) gather(p.json(), keys);
    }

    private static void gather(JsonElement el, Set<String> keys) {
        if (el == null) return;
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) gather(e, keys);
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        if (o.has("unlock_on"))
            for (UnlockCondition c : UnlockCondition.parseQuiet(o.get("unlock_on"))) keys.add(c.key());
        for (var e : o.entrySet())
            if (!e.getKey().equals("unlock_on")) gather(e.getValue(), keys);
    }

    public static Book view(Book book) {
        if (showAll()) return book;
        View v = views.get(book);
        if (v != null && v.stamp == stamp) return v.book;
        List<Book.Page> vis = new ArrayList<>();
        for (Book.Page p : book.pages()) if (pageShown(book, p)) vis.add(p);
        Book out = vis.size() == book.pages().size() ? book
                : new Book(book.id(), book.json(), book.vars(), vis, book.errors(), book.hidden(), new Flow.Cache());
        views.put(book, new View(stamp, out));
        return out;
    }

    static void forgetViews() {
        views.clear();
    }

    public static boolean shown(JsonElement el) {
        if (!BookRegistry.visible(el)) return false;
        if (!el.isJsonObject()) return true;
        return !hidden(el.getAsJsonObject(), null, null);
    }

    public static boolean greyed(JsonElement el) {
        if (showAll() || !el.isJsonObject()) return false;
        JsonObject o = el.getAsJsonObject();
        return o.has("unlock_on") && !unlocked(o) && mode(o, null, null).equals("show");
    }

    public static boolean pageShown(Book book, Book.Page page) {
        return !pageHidden(book, page);
    }

    public static boolean pageHidden(Book book, Identifier page) {
        for (Book.Page p : rawPages(book)) if (p.id().equals(page)) return pageHidden(book, p);
        return false;
    }

    public static boolean pageGreyed(Book book, Identifier page) {
        for (Book.Page p : rawPages(book)) if (p.id().equals(page)) return pageGreyed(book, p);
        return false;
    }

    public static boolean pageHidden(Book book, Book.Page page) {
        if (showAll()) return false;
        JsonObject sec = book.section(page.section());
        if (hidden(sec, book.json(), null)) return true;
        return hidden(page.json(), book.json(), sec);
    }

    public static boolean pageGreyed(Book book, Book.Page page) {
        if (showAll() || pageHidden(book, page)) return false;
        JsonObject sec = book.section(page.section());
        if (grey(sec, book.json(), null)) return true;
        return grey(page.json(), book.json(), sec);
    }

    public static boolean sectionGreyed(Book book, String section) {
        return !showAll() && grey(book.section(section), book.json(), null);
    }

    public static Component hint(Book book, Book.Page page) {
        JsonObject sec = book.section(page.section());
        if (page.json().has("unlock_on") && !unlocked(page.json())) return hintOf(page.json());
        if (sec.has("unlock_on") && !unlocked(sec)) return hintOf(sec);
        if (page.json().has("unlock_on")) return hintOf(page.json());
        return hintOf(sec);
    }

    public static Component hintOf(JsonObject o) {
        if (o.has("unlock_hint") && o.get("unlock_hint").isJsonPrimitive())
            return Texts.of(o.get("unlock_hint").getAsString());
        List<UnlockCondition> cs = o.has("unlock_on") ? UnlockCondition.parseQuiet(o.get("unlock_on")) : List.of();
        if (cs.isEmpty()) return Component.translatable("simplebooks.unlock.item", "?");
        Component out = hintOf(cs.get(0));
        for (int i = 1; i < cs.size(); i++) out = Component.translatable("simplebooks.unlock.or", out, hintOf(cs.get(i)));
        return out;
    }

    public static Component hintOf(UnlockCondition c) {
        return switch (c.kind()) {
            case ITEM -> Component.translatable("simplebooks.unlock.item", itemName(c.id()));
            case TAG -> tagHint(c.id());
            case ADVANCEMENT -> Component.translatable("simplebooks.unlock.advancement", advancementName(c.id()));
        };
    }

    private static boolean hidden(JsonObject o, @Nullable JsonObject book, @Nullable JsonObject section) {
        return o.has("unlock_on") && !unlocked(o) && mode(o, book, section).equals("hide");
    }

    private static boolean grey(JsonObject o, @Nullable JsonObject book, @Nullable JsonObject section) {
        return o.has("unlock_on") && !unlocked(o) && mode(o, book, section).equals("show");
    }

    private static String mode(JsonObject o, @Nullable JsonObject book, @Nullable JsonObject section) {
        if (o.has("locked") && o.get("locked").isJsonPrimitive()) return o.get("locked").getAsString();
        if (section != null && section.has("locked") && section.get("locked").isJsonPrimitive())
            return section.get("locked").getAsString();
        if (book != null && book.has("locked") && book.get("locked").isJsonPrimitive())
            return book.get("locked").getAsString();
        return "show";
    }

    private static boolean unlocked(JsonObject o) {
        if (showAll() || !o.has("unlock_on")) return true;
        List<UnlockCondition> cs = UnlockCondition.parseQuiet(o.get("unlock_on"));
        if (cs.isEmpty()) return true;
        for (UnlockCondition c : cs) if (have.contains(c.key())) return true;
        return false;
    }

    private static List<Book.Page> rawPages(Book book) {
        // a viewed book may have dropped hide-locked pages; prefer the original list when present
        for (var e : views.entrySet()) if (e.getValue().book == book) return e.getKey().pages();
        return book.pages();
    }

    private static Component itemName(Identifier id) {
        if (BuiltInRegistries.ITEM.containsKey(id))
            return new ItemStack(SimpleBooks.item(id)).getHoverName();
        return Component.literal(id.getPath().replace('_', ' '));
    }

    private static Component tagHint(Identifier id) {
        String key = "tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        if (Language.getInstance().has(key))
            return Component.translatable("simplebooks.unlock.tag", Component.translatable(key));
        return Component.translatable("simplebooks.unlock.tag_any",
                UnlockHints.tagPhrase(id.getPath()));
    }

    private static Component advancementName(Identifier id) {
        String path = id.getPath().replace('/', '.');
        String k1 = "advancements." + path + ".title";
        if (id.getNamespace().equals("minecraft") && Language.getInstance().has(k1)) return Component.translatable(k1);
        String k2 = "advancements." + id.getNamespace() + "." + path + ".title";
        if (Language.getInstance().has(k2)) return Component.translatable(k2);
        return Component.literal(id.getPath().replace('/', ' ').replace('_', ' '));
    }
}

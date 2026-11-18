package com.workshopcraft.simplebooks.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.UnlockAdminPacket;
import com.workshopcraft.simplebooks.UnlockCondition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class SimpleBooksClient {
    private SimpleBooksClient() {}

    public static void init() {
        SimpleBooks.opener = BookScreen::open;
        com.workshopcraft.simplebooks.quest.QuestClientStore.toasts = o -> net.minecraft.client.Minecraft.getInstance().execute(() -> QuestToast.show(o));
        com.workshopcraft.simplebooks.quest.QuestClientStore.boxes = HandInBoxScreen::open;
        com.workshopcraft.simplebooks.quest.QuestClientStore.opens = o -> {
            Identifier book = o.has("book") ? Identifier.tryParse(o.get("book").getAsString()) : null;
            if (book != null) BookScreen.open(book, o.has("spread") ? o.get("spread").getAsInt() : 0, -2, null);
        };
        com.workshopcraft.simplebooks.quest.QuestClientStore.langRefresh = () -> {};
    }

    /** /simplebooks open|validate|list, built for any client command source type. */
    public static <S> LiteralArgumentBuilder<S> command() {
        SuggestionProvider<S> books = (ctx, b) -> SharedSuggestionProvider.suggest(BookRegistry.ids().stream().map(Identifier::toString), b);
        return LiteralArgumentBuilder.<S>literal("simplebooks")
                .then(LiteralArgumentBuilder.<S>literal("open")
                        .then(RequiredArgumentBuilder.<S, String>argument("book", StringArgumentType.greedyString()).suggests(books)
                                .executes(ctx -> open(StringArgumentType.getString(ctx, "book")))))
                .then(LiteralArgumentBuilder.<S>literal("validate")
                        .then(RequiredArgumentBuilder.<S, String>argument("book", StringArgumentType.greedyString()).suggests(books)
                                .executes(ctx -> validate(StringArgumentType.getString(ctx, "book")))))
                .then(LiteralArgumentBuilder.<S>literal("unlocks")
                        .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
                                .then(LiteralArgumentBuilder.<S>literal("list").executes(ctx ->
                                        unlocks("list", StringArgumentType.getString(ctx, "player"), "")))
                                .then(LiteralArgumentBuilder.<S>literal("reset").executes(ctx ->
                                        unlocks("reset", StringArgumentType.getString(ctx, "player"), "")))
                                .then(LiteralArgumentBuilder.<S>literal("grant")
                                        .then(RequiredArgumentBuilder.<S, String>argument("target", StringArgumentType.greedyString())
                                                .executes(ctx -> unlocks("grant", StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "target")))))))
                .then(LiteralArgumentBuilder.<S>literal("list").executes(ctx -> {
                    say(Component.literal(BookRegistry.ids().size() + " book(s): " + String.join(", ", BookRegistry.ids().stream().map(Identifier::toString).toList())));
                    return 1;
                }));
    }

    private static int open(String arg) {
        String[] parts = arg.trim().split(" ");
        Identifier id = Identifier.tryParse(parts[0]);
        if (parts.length == 1 && (id == null || BookRegistry.get(id) == null) && parts[0].lastIndexOf('/') > parts[0].indexOf(':')) {
            int cut = parts[0].lastIndexOf('/');
            parts = new String[] {parts[0].substring(0, cut), parts[0].substring(cut + 1)};
            id = Identifier.tryParse(parts[0]);
        }
        if (id == null || BookRegistry.get(id) == null) {
            say(Component.translatable("simplebooks.unknown_book", parts[0]).withStyle(ChatFormatting.RED));
            return 0;
        }
        Identifier page = parts.length > 1 ? BookRegistry.resolveLink(BookRegistry.get(id), parts[1]) : null;
        BookScreen.open(id, page);
        return 1;
    }

    private static int validate(String arg) {
        Identifier id = Identifier.tryParse(arg.trim());
        Book b = id == null ? null : BookRegistry.raw(id);
        if (b == null) {
            say(Component.translatable("simplebooks.unknown_book", arg).withStyle(ChatFormatting.RED));
            return 0;
        }
        int bad = 0;
        for (String e : b.errors()) say(Component.literal(e).withStyle(ChatFormatting.RED));
        List<String> warnings = new ArrayList<>();
        unlockIssues(b.json(), "book", b.errors(), warnings);
        if (b.json().has("sections")) {
            int i = 0;
            for (JsonElement s : b.json().getAsJsonArray("sections")) {
                unlockIssues(s, "book /sections/" + i, b.errors(), warnings);
                i++;
            }
        }
        for (Book.Page p : b.pages()) unlockIssues(p.json(), p.file(), p.errors(), warnings);
        for (String w : warnings) say(Component.literal(w).withStyle(ChatFormatting.YELLOW));
        var font = Minecraft.getInstance().font;
        Flow f = b.errors().isEmpty() ? b.flow() : null;
        int last = f != null ? f.pages() - 1 : b.pages().size() * 2 - 1;
        java.util.Map<Book.Page, Float> room = new java.util.HashMap<>();
        for (int i = 0; i < b.pages().size(); i++) {
            Book.Page p = b.pages().get(i);
            List<String> errs = new ArrayList<>(p.errors());
            if (!p.broken() && b.errors().isEmpty()) {
                List<Spread> parts = new ArrayList<>();
                if (f == null) parts.add(new Spread(null, font, b, p, i));
                else {
                    errs.addAll(f.problems(p));
                    for (int h = f.pageOf(p.id()); h >= 0 && f.half(h) != null && f.half(h).page() == p; h++)
                        parts.add(new Spread(null, font, b, p, h / 2, f.half(h), h % 2 == 1, h + 1));
                }
                for (Spread s : parts) {
                    s.content();
                    errs.addAll(s.runtimeErrors());
                    for (String c : s.pageChecks()) if (!c.startsWith("page " + (last + 1) + " ")) errs.add(c);
                    for (String a : s.arrows()) com.mojang.logging.LogUtils.getLogger().info("[simplebooks arrows] {} {}", p.file(), a);
                }
                if (!parts.isEmpty()) room.put(p, parts.get(parts.size() - 1).roomLeft());
                // steps carried on in another file while this one's last page still had room for them
                if (i > 0 && p.json().has("first_step") && room.containsKey(b.pages().get(i - 1))) {
                    float first = new Spread(null, font, b, p, i).firstStepHeight();
                    if (first > 0 && room.get(b.pages().get(i - 1)) >= first)
                        errs.add("its first step would fit at the end of " + b.pages().get(i - 1).id().getPath());
                }
            }
            for (String e : errs) {
                say(Component.literal(p.file() + " " + e).withStyle(ChatFormatting.RED));
                bad++;
            }
        }
        String count = f != null ? f.pages() + " page(s) on " + f.spreads() + " spread(s), " : b.pages().size() + " page(s), ";
        say(Component.literal(count + (bad + b.errors().size()) + " problem(s)"));
        return 1;
    }

    private static int unlocks(String action, String player, String target) {
        if (Minecraft.getInstance().getConnection() == null) {
            say(Component.literal("Not connected to a server").withStyle(ChatFormatting.RED));
            return 0;
        }
        SimpleBooks.toServerUnlockAdmin.accept(new UnlockAdminPacket(action, player, target == null ? "" : target));
        return 1;
    }

    private static void unlockIssues(JsonElement el, String ptr, List<String> errors, List<String> warnings) {
        if (el == null) return;
        if (el.isJsonArray()) {
            for (int i = 0; i < el.getAsJsonArray().size(); i++)
                unlockIssues(el.getAsJsonArray().get(i), ptr + "/" + i, errors, warnings);
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        if (o.has("unlock_on")) {
            var cs = UnlockCondition.parse(o.get("unlock_on"), ptr + "/unlock_on", errors::add);
            for (var c : cs) switch (c.kind()) {
                case ITEM -> {
                    if (!ItemRef.known(c.id().toString())) warnings.add(ptr + "/unlock_on: unknown item " + c.id());
                }
                case TAG -> {
                    boolean empty = true;
                    for (var ignored : net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(
                            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, c.id()))) {
                        empty = false;
                        break;
                    }
                    if (empty) warnings.add(ptr + "/unlock_on: unknown or empty tag " + c.id());
                }
                case ADVANCEMENT -> {
                    String path = c.id().getPath().replace('/', '.');
                    String k1 = "advancements." + path + ".title";
                    String k2 = "advancements." + c.id().getNamespace() + "." + path + ".title";
                    var lang = net.minecraft.locale.Language.getInstance();
                    if (!lang.has(k1) && !lang.has(k2))
                        warnings.add(ptr + "/unlock_on: unknown advancement " + c.id());
                }
            }
        }
        if (o.has("locked")) UnlockCondition.checkLocked(o.get("locked"), ptr + "/locked", errors::add);
        for (var e : o.entrySet())
            if (!e.getKey().equals("unlock_on")) unlockIssues(e.getValue(), ptr + "/" + e.getKey(), errors, warnings);
    }

    private static void say(Component c) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(c);
        *///?} else if >=26.1 {
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(c);
        //?} else {
        /*Minecraft.getInstance().gui.getChat().addMessage(c);
        *///?}
    }
}

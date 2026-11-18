package com.workshopcraft.simplebooks;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.workshopcraft.simplebooks.quest.QuestMc;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Server-side book unlocks: the watch list, the per-player save, inventory and advancement checks, and /simplebooks unlocks. */
public final class Unlocks {
    public static BiConsumer<ServerPlayer, UnlockSyncPacket> sender = (p, x) -> {};
    private static @Nullable MinecraftServer server;
    private static @Nullable UnlockData data;
    private static final Set<UnlockCondition> watch = new LinkedHashSet<>();
    private static int ticks;

    private Unlocks() {}

    public static void start(MinecraftServer s) {
        server = s;
        data = UnlockData.get(s);
        watch.clear();
        ticks = 0;
        SimpleBooks.LOG.info("Book unlocks: {} player(s) saved, book_unlocks {}", data.players().size(), SimpleBooksConfig.get().bookUnlocks());
    }

    public static void stop() {
        server = null;
        data = null;
        watch.clear();
    }

    public static void login(ServerPlayer p) {
        if (data == null) return;
        scan(p);
        sync(p);
    }

    public static void tick(MinecraftServer s) {
        if (server != s || data == null) return;
        if (++ticks % 20 != 0) return;
        if (watch.isEmpty()) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) scan(p);
    }

    public static void configLoaded() {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    public static void advancement(ServerPlayer p, String id) {
        if (data == null) return;
        Identifier adv = Identifier.tryParse(id);
        if (adv == null) return;
        UnlockCondition c = new UnlockCondition(UnlockCondition.Kind.ADVANCEMENT, adv);
        if (!watch.contains(c)) return;
        grant(p.getUUID().toString(), c.key(), p);
    }

    public static void watch(ServerPlayer p, List<String> keys) {
        if (data == null) return;
        int added = 0;
        for (String key : keys) {
            if (watch.size() >= UnlockCondition.MAX) break;
            UnlockCondition c = UnlockCondition.parseKey(key);
            if (c == null) continue;
            if (watch.add(c)) added++;
        }
        if (added > 0) SimpleBooks.LOG.debug("Unlock watch: {} new from {}, {} total", added, p.getScoreboardName(), watch.size());
        scan(p);
        sync(p);
    }

    private static void scan(ServerPlayer p) {
        if (data == null || watch.isEmpty()) return;
        Set<String> have = data.of(p.getUUID().toString());
        List<UnlockCondition> need = new ArrayList<>();
        for (UnlockCondition c : watch) if (!have.contains(c.key())) need.add(c);
        if (need.isEmpty()) return;
        boolean changed = false;
        Set<Identifier> items = new LinkedHashSet<>();
        Set<TagKey<Item>> tags = new LinkedHashSet<>();
        List<UnlockCondition> adv = new ArrayList<>();
        for (UnlockCondition c : need) {
            switch (c.kind()) {
                case ITEM -> items.add(c.id());
                case TAG -> tags.add(TagKey.create(Registries.ITEM, c.id()));
                case ADVANCEMENT -> adv.add(c);
            }
        }
        if (!items.isEmpty() || !tags.isEmpty()) {
            var inv = p.getInventory();
            for (int i = 0; i < 41; i++) {
                if (grantStack(have, p, inv.getItem(i), items, tags)) changed = true;
            }
            if (grantStack(have, p, p.containerMenu.getCarried(), items, tags)) changed = true;
        }
        for (UnlockCondition c : adv) {
            if (hasAdvancement(p, c.id()) && have.add(c.key())) changed = true;
        }
        if (changed) {
            data.setDirty();
            sync(p);
        }
    }

    private static boolean grantStack(Set<String> have, ServerPlayer p, ItemStack stack, Set<Identifier> items, Set<TagKey<Item>> tags) {
        if (stack.isEmpty()) return false;
        boolean changed = false;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null && items.contains(id) && have.add(new UnlockCondition(UnlockCondition.Kind.ITEM, id).key()))
            changed = true;
        for (TagKey<Item> tag : tags) {
            if (stack.is(tag) && have.add(new UnlockCondition(UnlockCondition.Kind.TAG, tag.location()).key()))
                changed = true;
        }
        return changed;
    }

    private static boolean hasAdvancement(ServerPlayer p, Identifier id) {
        MinecraftServer s = QuestMc.server(p);
        if (s == null) return false;
        //? if >=1.20.2 {
        var a = s.getAdvancements().get(id);
        return a != null && p.getAdvancements().getOrStartProgress(a).isDone();
        //?} else {
        /*var a = s.getAdvancements().getAdvancement(id);
        return a != null && p.getAdvancements().getOrStartProgress(a).isDone();
        *///?}
    }

    private static boolean advancementExists(MinecraftServer s, Identifier id) {
        //? if >=1.20.2 {
        return s.getAdvancements().get(id) != null;
        //?} else {
        /*return s.getAdvancements().getAdvancement(id) != null;
        *///?}
    }

    private static void grant(String uuid, String key, @Nullable ServerPlayer online) {
        if (data == null) return;
        if (data.of(uuid).add(key)) {
            data.setDirty();
            if (online != null) sync(online);
        }
    }

    private static void sync(ServerPlayer p) {
        if (data == null) return;
        List<String> keys = List.copyOf(data.of(p.getUUID().toString()));
        sender.accept(p, new UnlockSyncPacket(SimpleBooksConfig.get().bookUnlocks(), keys));
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        var root = Commands.literal("simplebooks")
                .then(Commands.literal("unlocks")
                        .requires(
                                //? if >=26.1 {
                                Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)
                                //?} else {
                                /*s -> s.hasPermission(2)
                                *///?}
                        )
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
                                .then(Commands.literal("list").executes(c -> run(c, "list", null)))
                                .then(Commands.literal("reset").executes(c -> run(c, "reset", null)))
                                .then(Commands.literal("grant").then(Commands.argument("target", StringArgumentType.greedyString())
                                        .suggests((c, b) -> {
                                            List<String> ids = new ArrayList<>();
                                            for (UnlockCondition x : watch) ids.add(x.key());
                                            return SharedSuggestionProvider.suggest(ids, b);
                                        })
                                        .executes(c -> run(c, "grant", StringArgumentType.getString(c, "target")))))));
        d.register(root);
    }

    private static int run(CommandContext<CommandSourceStack> c, String action, @Nullable String target) {
        return apply(c.getSource(), action, StringArgumentType.getString(c, "player"), target);
    }

    public static void admin(ServerPlayer src, String action, String who, String target) {
        if (!QuestMc.op(src)) {
            src.sendSystemMessage(Component.literal("You need to be an operator to do that").withStyle(ChatFormatting.RED));
            return;
        }
        apply(src.createCommandSourceStack(), action, who, target);
    }

    private static int apply(CommandSourceStack src, String action, String who, @Nullable String target) {
        if (data == null || server == null) {
            src.sendFailure(Component.literal("Unlocks are not loaded"));
            return 0;
        }
        Resolved r = resolve(who);
        if (r == null) {
            src.sendFailure(Component.literal("No player " + who));
            return 0;
        }
        Set<String> have = data.of(r.uuid);
        switch (action) {
            case "list" -> {
                if (have.isEmpty()) src.sendSuccess(() -> Component.literal(r.name + " has no book unlocks"), false);
                else {
                    src.sendSuccess(() -> Component.literal(r.name + " has " + have.size() + " book unlock(s):"), false);
                    for (String k : have) src.sendSuccess(() -> Component.literal("  " + k), false);
                }
                return have.size();
            }
            case "reset" -> {
                int n = have.size();
                have.clear();
                data.setDirty();
                if (r.player != null) sync(r.player);
                src.sendSuccess(() -> Component.literal("Reset " + n + " unlock(s) for " + r.name), true);
                return 1;
            }
            case "grant" -> {
                if (target == null || target.isBlank()) {
                    src.sendFailure(Component.literal("Missing item, tag or advancement"));
                    return 0;
                }
                boolean item = Identifier.tryParse(target) != null && BuiltInRegistries.ITEM.containsKey(Identifier.tryParse(target));
                boolean adv = false;
                Identifier maybe = Identifier.tryParse(target.startsWith("advancement:") ? target.substring("advancement:".length()) : target);
                if (maybe != null) adv = advancementExists(server, maybe);
                UnlockCondition c = UnlockCondition.parseGrant(target, item, adv);
                if (c == null) {
                    src.sendFailure(Component.literal("Bad id " + target));
                    return 0;
                }
                if (have.add(c.key())) {
                    data.setDirty();
                    if (r.player != null) sync(r.player);
                    src.sendSuccess(() -> Component.literal("Granted " + c.key() + " to " + r.name), true);
                    return 1;
                }
                src.sendSuccess(() -> Component.literal(r.name + " already has " + c.key()), false);
                return 0;
            }
            default -> {
                src.sendFailure(Component.literal("Unknown action " + action));
                return 0;
            }
        }
    }

    private record Resolved(String uuid, String name, @Nullable ServerPlayer player) {}

    private static @Nullable Resolved resolve(String who) {
        if (server == null) return null;
        ServerPlayer online = server.getPlayerList().getPlayerByName(who);
        if (online != null) return new Resolved(online.getUUID().toString(), online.getScoreboardName(), online);
        UUID id = QuestMc.byName(server, who);
        if (id == null) return null;
        return new Resolved(id.toString(), QuestMc.name(server, id), server.getPlayerList().getPlayer(id));
    }
}

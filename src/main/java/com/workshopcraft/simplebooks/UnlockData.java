package com.workshopcraft.simplebooks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
//? if >=1.21.5 {
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
//?} else if >=1.20.5 {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixTypes;
*///?}

/** The world's book-unlock save. A save it cannot read is copied to a backup and replaced with a fresh one. */
public class UnlockData extends SavedData {
    public static final String NAME = "simplebooks_unlocks";
    private final Map<String, Set<String>> players = new LinkedHashMap<>();
    private @Nullable String bad;
    private static @Nullable MinecraftServer checked;

    //? if >=1.21.5 {
    public static final SavedDataType<UnlockData> TYPE = new SavedDataType<>(SimpleBooks.id("unlocks"), UnlockData::new,
            CompoundTag.CODEC.xmap(UnlockData::load, UnlockData::toTag), fix());
    //?} else if >=1.20.5 {
    /*public static final SavedData.Factory<UnlockData> FACTORY = new SavedData.Factory<>(UnlockData::new, (tag, p) -> load(tag), fix());
    *///?}

    //? if >=1.20.5 {
    private static DataFixTypes fix() {
        //? if fabric {
        return DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES;
        //?} else {
        /*return null;
        *///?}
    }
    //?}

    public static UnlockData get(MinecraftServer server) {
        if (checked != server) {
            checked = server;
            checkFile(server);
        }
        //? if >=1.21.5 {
        UnlockData d = server.getDataStorage().computeIfAbsent(TYPE);
        //?} else if >=1.20.5 {
        /*UnlockData d = server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        *///?} else {
        /*UnlockData d = server.overworld().getDataStorage().computeIfAbsent(UnlockData::load, UnlockData::new, NAME);
        *///?}
        if (d.bad != null) {
            backup(server, d.bad);
            d.bad = null;
            d.setDirty();
        }
        return d;
    }

    static Path file(MinecraftServer server) {
        Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
        //? if >=1.21.5 {
        return data.resolve("simplebooks").resolve("unlocks.dat");
        //?} else {
        /*return data.resolve(NAME + ".dat");
        *///?}
    }

    private static void checkFile(MinecraftServer server) {
        Path file = file(server);
        if (!Files.isRegularFile(file)) return;
        try {
            byte[] b = Files.readAllBytes(file);
            if (b.length > 1 && (b[0] & 0xff) == 0x1f && (b[1] & 0xff) == 0x8b) {
                //? if >=1.20.2 {
                NbtIo.readCompressed(new ByteArrayInputStream(b), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                //?} else {
                /*NbtIo.readCompressed(new ByteArrayInputStream(b));
                *///?}
            } else {
                NbtIo.read(new DataInputStream(new ByteArrayInputStream(b)));
            }
        } catch (Exception | StackOverflowError e) {
            backup(server, "could not be read (" + e + ")");
            try {
                Files.delete(file);
            } catch (IOException ignored) {
            }
        }
    }

    private static void backup(MinecraftServer server, String why) {
        Path file = file(server);
        Path copy = file.resolveSibling(file.getFileName() + "." + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".bak");
        try {
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            SimpleBooks.LOG.warn("Unlock save {}. A copy is at {} and unlocks start fresh.", why, copy);
        } catch (Exception e) {
            SimpleBooks.LOG.warn("Unlock save {}. Could not copy it to {} ({}), unlocks start fresh.", why, copy, e.toString());
        }
    }

    public Set<String> of(String uuid) {
        return players.computeIfAbsent(uuid, k -> new LinkedHashSet<>());
    }

    public Map<String, Set<String>> players() {
        return players;
    }

    static UnlockData load(CompoundTag tag) {
        UnlockData d = new UnlockData();
        int version = tag.contains("version") ? number(tag.get("version")) : UnlockSave.VERSION;
        if (version > UnlockSave.VERSION) {
            d.bad = "is version " + version + " from a newer Simple Books (this one reads up to " + UnlockSave.VERSION + ")";
            return d;
        }
        try {
            var result = UnlockSave.Data.CODEC.parse(NbtOps.INSTANCE, tag);
            var error = result.error();
            if (error.isPresent() || result.result().isEmpty()) {
                d.bad = "could not be read (" + error.map(Object::toString).orElse("empty") + ")";
                return d;
            }
            result.result().get().players().forEach((k, v) -> d.players.put(k, new LinkedHashSet<>(v)));
        } catch (Exception e) {
            d.bad = "could not be read (" + e + ")";
        }
        return d;
    }

    private static int number(@Nullable Tag t) {
        return t instanceof net.minecraft.nbt.NumericTag n ?
                //? if >=1.21.5 {
                n.intValue()
                //?} else {
                /*n.getAsInt()
                *///?}
                : -1;
    }

    CompoundTag toTag() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        players.forEach((k, v) -> {
            if (!v.isEmpty()) map.put(k, new ArrayList<>(v));
        });
        Tag t = UnlockSave.Data.CODEC.encodeStart(NbtOps.INSTANCE, new UnlockSave.Data(UnlockSave.VERSION, map)).getOrThrow(
                //? if <1.20.5 {
                /*false, e -> SimpleBooks.LOG.error("Unlock save could not be written: {}", e)
                *///?}
        );
        return (CompoundTag) t;
    }

    //? if >=1.20.5 && <1.21.5 {
    /*@Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return save(tag);
    }
    *///?}

    //? if <1.21.5 {
    /*public CompoundTag save(CompoundTag tag) {
        CompoundTag t = toTag();
        for (String k : t.getAllKeys()) tag.put(k, t.get(k));
        return tag;
    }
    *///?}
}

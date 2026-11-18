package com.workshopcraft.simplebooks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/** Per-player unlock keys, saved under the world like the quest save. */
public final class UnlockSave {
    public static final int VERSION = 1;

    private UnlockSave() {}

    public record Data(int version, Map<String, List<String>> players) {
        public static final Data EMPTY = new Data(VERSION, Map.of());
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("version").forGetter(Data::version),
                Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("players", Map.of()).forGetter(Data::players)
        ).apply(i, Data::new));
    }
}

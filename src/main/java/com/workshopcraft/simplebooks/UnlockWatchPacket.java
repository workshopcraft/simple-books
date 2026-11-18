package com.workshopcraft.simplebooks;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.ArrayList;
import java.util.List;

/** Client to server: the unlock conditions found in the client's loaded books. */
//? if >=1.20.5 {
public record UnlockWatchPacket(String json) implements CustomPacketPayload {
    public static final Type<UnlockWatchPacket> TYPE = new Type<>(SimpleBooks.id("unlock_watch"));
    public static final StreamCodec<ByteBuf, UnlockWatchPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UnlockWatchPacket::json,
            UnlockWatchPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record UnlockWatchPacket(String json) {
*///?}
    public static final net.minecraft.resources.Identifier ID = SimpleBooks.id("unlock_watch");

    public UnlockWatchPacket(List<String> keys) {
        this(toJson(keys));
    }

    public List<String> keys() {
        return fromJson(json);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(json);
    }

    public static UnlockWatchPacket read(FriendlyByteBuf buf) {
        return new UnlockWatchPacket(buf.readUtf());
    }

    public void handle(ServerPlayer player) {
        Unlocks.watch(player, keys());
    }

    private static String toJson(List<String> keys) {
        JsonArray a = new JsonArray();
        int n = Math.min(keys.size(), UnlockCondition.MAX);
        for (int i = 0; i < n; i++) a.add(keys.get(i));
        return a.toString();
    }

    private static List<String> fromJson(String json) {
        List<String> out = new ArrayList<>();
        try {
            JsonArray a = JsonParser.parseString(json).getAsJsonArray();
            int n = Math.min(a.size(), UnlockCondition.MAX);
            for (int i = 0; i < n; i++) out.add(a.get(i).getAsString());
        } catch (Exception ignored) {
        }
        return out;
    }
}

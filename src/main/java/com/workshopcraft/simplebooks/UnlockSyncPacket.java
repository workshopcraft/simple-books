package com.workshopcraft.simplebooks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.ArrayList;
import java.util.List;

/** Server to client: whether gating is on, and the player's unlocked condition keys. */
//? if >=1.20.5 {
public record UnlockSyncPacket(String json) implements CustomPacketPayload {
    public static final Type<UnlockSyncPacket> TYPE = new Type<>(SimpleBooks.id("unlock_sync"));
    public static final StreamCodec<ByteBuf, UnlockSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UnlockSyncPacket::json,
            UnlockSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record UnlockSyncPacket(String json) {
*///?}
    public static final net.minecraft.resources.Identifier ID = SimpleBooks.id("unlock_sync");

    public UnlockSyncPacket(boolean enabled, List<String> keys) {
        this(toJson(enabled, keys));
    }

    public boolean enabled() {
        try {
            return JsonParser.parseString(json).getAsJsonObject().get("enabled").getAsBoolean();
        } catch (Exception e) {
            return true;
        }
    }

    public List<String> keys() {
        List<String> out = new ArrayList<>();
        try {
            JsonArray a = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("keys");
            if (a == null) return out;
            int n = Math.min(a.size(), UnlockCondition.MAX * 2);
            for (int i = 0; i < n; i++) out.add(a.get(i).getAsString());
        } catch (Exception ignored) {
        }
        return out;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(json);
    }

    public static UnlockSyncPacket read(FriendlyByteBuf buf) {
        return new UnlockSyncPacket(buf.readUtf());
    }

    public void handle() {
        com.workshopcraft.simplebooks.client.UnlockClient.accept(enabled(), keys());
    }

    private static String toJson(boolean enabled, List<String> keys) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        JsonArray a = new JsonArray();
        for (String k : keys) a.add(k);
        o.add("keys", a);
        return o.toString();
    }
}

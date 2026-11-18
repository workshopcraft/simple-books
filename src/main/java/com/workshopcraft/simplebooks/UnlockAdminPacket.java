package com.workshopcraft.simplebooks;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** Client to server: /simplebooks unlocks, used so the client command does not hide the server one. */
//? if >=1.20.5 {
public record UnlockAdminPacket(String action, String player, String target) implements CustomPacketPayload {
    public static final Type<UnlockAdminPacket> TYPE = new Type<>(SimpleBooks.id("unlock_admin"));
    public static final StreamCodec<ByteBuf, UnlockAdminPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UnlockAdminPacket::action,
            ByteBufCodecs.STRING_UTF8, UnlockAdminPacket::player,
            ByteBufCodecs.STRING_UTF8, UnlockAdminPacket::target,
            UnlockAdminPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record UnlockAdminPacket(String action, String player, String target) {
*///?}
    public static final net.minecraft.resources.Identifier ID = SimpleBooks.id("unlock_admin");

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(action, 16);
        buf.writeUtf(player, 64);
        buf.writeUtf(target, 256);
    }

    public static UnlockAdminPacket read(FriendlyByteBuf buf) {
        return new UnlockAdminPacket(buf.readUtf(16), buf.readUtf(64), buf.readUtf(256));
    }

    public void handle(ServerPlayer src) {
        Unlocks.admin(src, action, player, target);
    }
}

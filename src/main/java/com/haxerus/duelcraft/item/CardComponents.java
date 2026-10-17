package com.haxerus.duelcraft.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CardComponents {
    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "duelcraft");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CARD_CODE =
            TYPES.register("card_code", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(1, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT.map(CardComponents::positiveCode, CardComponents::positiveCode))
                    .build());

    public static int positiveCode(int code) {
        if (code <= 0) throw new IllegalArgumentException("Card passcode must be positive");
        return code;
    }
}

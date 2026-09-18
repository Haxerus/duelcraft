package com.haxerus.duelcraft.collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;

import java.math.BigDecimal;

final class CollectionCodecs {
    // Default numeric codecs coerce with Number.intValue()/longValue(), losing fractions or overflow.
    static final Codec<Long> LONG = new PrimitiveCodec<>() {
        @Override public <T> DataResult<Long> read(DynamicOps<T> ops, T input) {
            return ops.getNumberValue(input).flatMap(number -> {
                try {
                    return DataResult.success(new BigDecimal(number.toString()).longValueExact());
                } catch (ArithmeticException | NumberFormatException exception) {
                    return DataResult.error(() -> "Expected a signed 64-bit integer");
                }
            });
        }
        @Override public <T> T write(DynamicOps<T> ops, Long value) { return ops.createLong(value); }
    };
    static final Codec<Integer> INT = new PrimitiveCodec<>() {
        @Override public <T> DataResult<Integer> read(DynamicOps<T> ops, T input) {
            return CollectionCodecs.LONG.parse(ops, input).flatMap(value -> value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                    ? DataResult.success(value.intValue()) : DataResult.error(() -> "Expected a signed 32-bit integer"));
        }
        @Override public <T> T write(DynamicOps<T> ops, Integer value) { return ops.createInt(value); }
    };
    static final Codec<Integer> PASSCODE = INT.validate(code -> code > 0 ? DataResult.success(code)
            : DataResult.error(() -> "Card passcodes must be positive"));

    private CollectionCodecs() {}
}

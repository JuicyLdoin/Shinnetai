package net.ldoin.shinnetai.buffered.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ByteUtil {

    public static final Map<Integer, Long> BIT_MASKS;

    static {
        Map<Integer, Long> masks = new HashMap<>();
        for (int i = 1; i <= 64; i++) {
            if (i == 64) {
                masks.put(i, 0xFFFFFFFFFFFFFFFFL);
            } else {
                masks.put(i, (1L << i) - 1L);
            }
        }

        BIT_MASKS = Map.copyOf(masks);
    }

    public static int calculateBitsNeeded(long number) {
        if (number < 0) {
            return 64;
        }

        if (number == 0) {
            return 1;
        }

        return 64 - Long.numberOfLeadingZeros(number);
    }

    public static int calculateBytesNeeded(int bits) {
        return (int) (Math.ceil((double) bits / 8));
    }

    public static byte[] numberToBytes(Number num) {
        return switch (num) {
            case BigInteger i -> i.toByteArray();
            case BigDecimal d -> d.toBigInteger().toByteArray();
            case Integer ignored -> ByteBuffer.allocate(4).putInt(num.intValue()).array();
            case Long ignored -> ByteBuffer.allocate(8).putLong(num.longValue()).array();
            case Double ignored -> ByteBuffer.allocate(8).putDouble(num.doubleValue()).array();
            case Float ignored -> ByteBuffer.allocate(4).putFloat(num.floatValue()).array();
            case Short ignored -> ByteBuffer.allocate(2).putShort(num.shortValue()).array();
            case Byte ignored -> new byte[]{num.byteValue()};
            default -> throw new IllegalArgumentException("Unsupported type: " + num.getClass());
        };
    }
}
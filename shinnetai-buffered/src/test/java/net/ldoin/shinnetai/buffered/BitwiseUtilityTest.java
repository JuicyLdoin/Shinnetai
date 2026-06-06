package net.ldoin.shinnetai.buffered;

import net.ldoin.shinnetai.buffered.bitwise.*;
import net.ldoin.shinnetai.buffered.bitwise.entry.BitwiseSerializerBooleanEntry;
import net.ldoin.shinnetai.buffered.bitwise.entry.BitwiseSerializerEnumEntry;
import net.ldoin.shinnetai.buffered.bitwise.entry.BitwiseSerializerFlagsEntry;
import net.ldoin.shinnetai.buffered.bitwise.entry.BitwiseSerializerNumberEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BitwiseUtilityTest {

    private long roundTrip(Number value, int bits) {
        BitwiseSerializerNumberEntry wEntry = new BitwiseSerializerNumberEntry(value, bits);
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(wEntry);
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry rEntry = new BitwiseSerializerNumberEntry(bits);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(rEntry);
        deser.unpack(packed);
        return rEntry.longValue();
    }

    @Test
    void trainInitialState() {
        BitwiseTrain train = new BitwiseTrain(8);
        assertEquals(0, train.currentIndex());
        assertEquals(0, train.currentIndexInByte());
        assertEquals(8, train.availableInCurrent());
    }

    @Test
    void trainAfter3Bits() {
        BitwiseTrain train = new BitwiseTrain(8);
        train.incrementIndexByBits(3);
        assertEquals(0, train.currentIndex());
        assertEquals(3, train.currentIndexInByte());
        assertEquals(5, train.availableInCurrent());
    }

    @Test
    void trainAtByteBoundary() {
        BitwiseTrain train = new BitwiseTrain(16);
        train.incrementIndexByBits(8);
        assertEquals(1, train.currentIndex());
        assertEquals(0, train.currentIndexInByte());
        assertEquals(8, train.availableInCurrent());
    }

    @Test
    void trainAfter11Bits() {
        BitwiseTrain train = new BitwiseTrain(16);
        train.incrementIndexByBits(11);
        assertEquals(1, train.currentIndex());
        assertEquals(3, train.currentIndexInByte());
        assertEquals(5, train.availableInCurrent());
    }

    @Test
    void bit1_zero() {
        assertEquals(0L, roundTrip((byte) 0, 1));
    }

    @Test
    void bit1_one() {
        assertEquals(1L, roundTrip((byte) 1, 1));
    }

    @Test
    void bit3_value5() {
        assertEquals(5L, roundTrip((byte) 5, 3));
    }

    @Test
    void bit3_maxValue7() {
        assertEquals(7L, roundTrip((byte) 7, 3));
    }

    @Test
    void bit8_byte127() {
        assertEquals(127L, roundTrip((byte) 127, 8));
    }

    @Test
    void bit8_int42() {
        assertEquals(42L, roundTrip(42, 8));
    }

    @Test
    void bit10_short1000() {
        assertEquals(1000L, roundTrip((short) 1000, 10));
    }

    @Test
    void bit16_int56732() {
        assertEquals(56732L, roundTrip(56732, 16));
    }

    @Test
    void bit16_maxUnsigned() {
        assertEquals(65535L, roundTrip(65535, 16));
    }

    @Test
    void bit27_long123456789() {
        assertEquals(123456789L, roundTrip(123456789L, 27));
    }

    @Test
    void bit32_intMaxValue() {
        assertEquals(Integer.MAX_VALUE, roundTrip(Integer.MAX_VALUE, 31));
    }

    @Test
    void bit63_longMaxValue() {
        assertEquals(Long.MAX_VALUE, roundTrip(Long.MAX_VALUE, 63));
    }

    @Test
    void twoValuesInOneByte_3bitsAnd5bits() {
        int typeId = 6;
        int channelId = 22;

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(typeId, 3));
        ser.addEntry(new BitwiseSerializerNumberEntry(channelId, 5));
        byte[] packed = ser.pack();
        assertEquals(1, packed.length);

        BitwiseSerializerNumberEntry rType = new BitwiseSerializerNumberEntry(3);
        BitwiseSerializerNumberEntry rChannel = new BitwiseSerializerNumberEntry(5);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(rType);
        deser.addEntry(rChannel);
        deser.unpack(packed);

        assertEquals(typeId, rType.intValue());
        assertEquals(channelId, rChannel.intValue());
    }

    @Test
    void twoValuesInOneByte_allZeros() {
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(0, 3));
        ser.addEntry(new BitwiseSerializerNumberEntry(0, 5));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry ra = new BitwiseSerializerNumberEntry(3);
        BitwiseSerializerNumberEntry rb = new BitwiseSerializerNumberEntry(5);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(ra);
        deser.addEntry(rb);
        deser.unpack(packed);

        assertEquals(0, ra.intValue());
        assertEquals(0, rb.intValue());
    }

    @Test
    void threeValuesCrossingBytes_5_6_7bits() {
        int a = 31;
        int b = 63;
        int c = 127;

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(a, 5));
        ser.addEntry(new BitwiseSerializerNumberEntry(b, 6));
        ser.addEntry(new BitwiseSerializerNumberEntry(c, 7));
        byte[] packed = ser.pack();
        assertEquals(3, packed.length);

        BitwiseSerializerNumberEntry ra = new BitwiseSerializerNumberEntry(5);
        BitwiseSerializerNumberEntry rb = new BitwiseSerializerNumberEntry(6);
        BitwiseSerializerNumberEntry rc = new BitwiseSerializerNumberEntry(7);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(ra);
        deser.addEntry(rb);
        deser.addEntry(rc);
        deser.unpack(packed);

        assertEquals(a, ra.intValue());
        assertEquals(b, rb.intValue());
        assertEquals(c, rc.intValue());
    }

    @Test
    void crossByteBoundary_valueSpansExactlyTwoBytes() {
        int first = 5;
        int second = 300;

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(first, 3));
        ser.addEntry(new BitwiseSerializerNumberEntry(second, 9));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry ra = new BitwiseSerializerNumberEntry(3);
        BitwiseSerializerNumberEntry rb = new BitwiseSerializerNumberEntry(9);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(ra);
        deser.addEntry(rb);
        deser.unpack(packed);

        assertEquals(first, ra.intValue());
        assertEquals(second, rb.intValue());
    }

    @Test
    void booleanAccessor_trueAndFalse() {
        BitwiseSerializerNumberEntry eTrue = new BitwiseSerializerNumberEntry((byte) 1, 1);
        BitwiseSerializerNumberEntry eFalse = new BitwiseSerializerNumberEntry((byte) 0, 1);
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(eTrue);
        ser.addEntry(eFalse);
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry rTrue = new BitwiseSerializerNumberEntry(1);
        BitwiseSerializerNumberEntry rFalse = new BitwiseSerializerNumberEntry(1);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(rTrue);
        deser.addEntry(rFalse);
        deser.unpack(packed);

        assertTrue(rTrue.booleanValue());
        assertFalse(rFalse.booleanValue());
    }

    @Test
    void eightOneBitValues() {
        int[] vals = {1, 0, 1, 1, 0, 0, 1, 0};
        BitwiseSerializer ser = new BitwiseSerializer();
        for (int v : vals) {
            ser.addEntry(new BitwiseSerializerNumberEntry(v, 1));
        }

        byte[] packed = ser.pack();
        assertEquals(1, packed.length);

        BitwiseSerializerNumberEntry[] reads = new BitwiseSerializerNumberEntry[8];
        BitwiseSerializer deser = new BitwiseSerializer();
        for (int i = 0; i < 8; i++) {
            reads[i] = new BitwiseSerializerNumberEntry(1);
            deser.addEntry(reads[i]);
        }

        deser.unpack(packed);
        for (int i = 0; i < 8; i++) {
            assertEquals(vals[i], reads[i].intValue(), "mismatch at index " + i);
        }
    }

    @Test
    void float32_positiveValue() {
        float orig = 3.14159f;
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(orig, 32));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(32, Float.class);
        new BitwiseSerializer().addEntry(r);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertEquals(orig, r.floatValue(), 0f);
    }

    @Test
    void float32_negativeValue() {
        float orig = -0.000123f;
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(orig, 32));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(32, Float.class);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertEquals(orig, r.floatValue(), 0f);
    }

    @Test
    void float32_nan() {
        float orig = Float.NaN;
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(orig, 32));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(32, Float.class);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertTrue(Float.isNaN(r.floatValue()));
    }

    @Test
    void float32_positiveInfinity() {
        float orig = Float.POSITIVE_INFINITY;
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(orig, 32));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(32, Float.class);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertTrue(Float.isInfinite(r.floatValue()));
        assertTrue(r.floatValue() > 0);
    }

    @Test
    void double64_pi() {
        double orig = Math.PI;
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(orig, 64));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(64, Double.class);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertEquals(orig, r.doubleValue(), 0.0);
    }

    @Test
    void double64_negativeInfinity() {
        double orig = Double.NEGATIVE_INFINITY;
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(orig, 64));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(64, Double.class);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertTrue(Double.isInfinite(r.doubleValue()));
        assertTrue(r.doubleValue() < 0);
    }

    @Test
    void namedEntry_nameIsPreserved() {
        BitwiseSerializerNumberEntry e = new BitwiseSerializerNumberEntry("packetType", 3);
        assertEquals("packetType", e.getName());
    }

    @Test
    void namedEntry_writeAndRead() {
        int typeId = 5;
        int channelId = 18;

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry("packetType", typeId, 3));
        ser.addEntry(new BitwiseSerializerNumberEntry("channelId", channelId, 5));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry rType = new BitwiseSerializerNumberEntry("packetType", 3);
        BitwiseSerializerNumberEntry rChannel = new BitwiseSerializerNumberEntry("channelId", 5);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(rType);
        deser.addEntry(rChannel);
        deser.unpack(packed);

        assertEquals(typeId, rType.intValue());
        assertEquals(channelId, rChannel.intValue());
        assertEquals("packetType", rType.getName());
        assertEquals("channelId", rChannel.getName());
    }

    @Test
    void toStringContainsName() {
        BitwiseSerializerNumberEntry e = new BitwiseSerializerNumberEntry("myField", 42, 8);
        String s = e.toString();
        assertTrue(s.contains("myField"));
        assertTrue(s.contains("Integer"));
        assertTrue(s.contains("42"));
    }

    @Test
    void typePreserved_byte_isRestoredAsByte() {
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry((byte) 99, 8));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(8, Byte.class);
        new BitwiseSerializer().addEntry(r);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertEquals((byte) 99, r.byteValue());
    }

    @Test
    void typePreserved_short_isRestoredAsShort() {
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry((short) 30000, 16));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(16, Short.class);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertEquals((short) 30000, r.shortValue());
    }

    @Test
    void typePreserved_integer_isRestoredAsInt() {
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(1_000_000, 20));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(20, Integer.class);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertEquals(1_000_000, r.intValue());
    }

    @Test
    void readOverflow_throwsMeaningfulException() {
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerNumberEntry(42, 8));
        byte[] packed = ser.pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry(16);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);

        IndexOutOfBoundsException ex = assertThrows(IndexOutOfBoundsException.class, () -> deser.unpack(packed));
        assertTrue(ex.getMessage().contains("overflow") || ex.getMessage().contains("buffer size"),
                "Expected a descriptive overflow message, got: " + ex.getMessage());
    }

    @Test
    void trainIncrementOverflow_throwsMeaningfulException() {
        BitwiseTrain train = new BitwiseTrain(8);
        assertThrows(IndexOutOfBoundsException.class, () -> train.incrementIndexByBits(9));
    }

    @Test
    void trainWriteReadBits_singleByte() {
        BitwiseTrain train = new BitwiseTrain(8);
        train.writeBits(0b10110101L, 8);

        BitwiseTrain read = new BitwiseTrain(train.bytes());
        assertEquals(0b10110101L, read.readBits(8));
    }

    @Test
    void trainWriteReadBits_crossBoundary() {
        BitwiseTrain train = new BitwiseTrain(16);
        train.writeBits(0b10101L, 5);
        train.writeBits(0b1100011L, 7);

        BitwiseTrain read = new BitwiseTrain(train.bytes());
        assertEquals(0b10101L, read.readBits(5));
        assertEquals(0b1100011L, read.readBits(7));
    }

    @Test
    void booleanEntry_true() {
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerBooleanEntry(true));
        byte[] packed = ser.pack();

        BitwiseSerializerBooleanEntry r = new BitwiseSerializerBooleanEntry();
        new BitwiseSerializer().addEntry(r);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertTrue(r.getValue());
    }

    @Test
    void booleanEntry_false() {
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerBooleanEntry(false));
        byte[] packed = ser.pack();

        BitwiseSerializerBooleanEntry r = new BitwiseSerializerBooleanEntry();
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertFalse(r.getValue());
    }

    @Test
    void booleanEntry_namedPreserved() {
        BitwiseSerializerBooleanEntry e = new BitwiseSerializerBooleanEntry("reliable", true);
        assertEquals("reliable", e.getName());
        assertTrue(e.getValue());
    }

    @Test
    void booleanEntry_mixedWithNumber() {
        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerBooleanEntry(true));
        ser.addEntry(new BitwiseSerializerNumberEntry(11, 4));
        ser.addEntry(new BitwiseSerializerBooleanEntry(false));
        byte[] packed = ser.pack();
        assertEquals(1, packed.length);

        BitwiseSerializerBooleanEntry r1 = new BitwiseSerializerBooleanEntry();
        BitwiseSerializerNumberEntry r2 = new BitwiseSerializerNumberEntry(4);
        BitwiseSerializerBooleanEntry r3 = new BitwiseSerializerBooleanEntry();
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r1);
        deser.addEntry(r2);
        deser.addEntry(r3);
        deser.unpack(packed);

        assertTrue(r1.getValue());
        assertEquals(11, r2.intValue());
        assertFalse(r3.getValue());
    }

    enum PacketType {HANDSHAKE, PING, PONG, DATA, ACK, CLOSE, ERROR, UNKNOWN}
    enum ConnectionState {CONNECTING, CONNECTED, CLOSING, CLOSED}
    enum Priority {NORMAL, HIGH}

    @Test
    void enumEntry_bitsCalculatedCorrectly() {
        BitwiseSerializerEnumEntry<PacketType> e = new BitwiseSerializerEnumEntry<>(PacketType.HANDSHAKE);
        assertEquals(3, e.bits());

        BitwiseSerializerEnumEntry<ConnectionState> z = new BitwiseSerializerEnumEntry<>(ConnectionState.CONNECTED);
        assertEquals(2, z.bits());

        BitwiseSerializerEnumEntry<Priority> c = new BitwiseSerializerEnumEntry<>(Priority.NORMAL);
        assertEquals(1, c.bits());
    }

    @Test
    void enumEntry_allValues() {
        for (PacketType type : PacketType.values()) {
            BitwiseSerializer ser = new BitwiseSerializer();
            ser.addEntry(new BitwiseSerializerEnumEntry<>(type));
            byte[] packed = ser.pack();

            BitwiseSerializerEnumEntry<PacketType> r = new BitwiseSerializerEnumEntry<>(PacketType.class);
            BitwiseSerializer deser = new BitwiseSerializer();
            deser.addEntry(r);
            deser.unpack(packed);

            assertEquals(type, r.getValue(), "Failed for " + type);
        }
    }

    @Test
    void enumEntry_named() {
        BitwiseSerializerEnumEntry<ConnectionState> e =
                new BitwiseSerializerEnumEntry<>("state", ConnectionState.CLOSING);
        assertEquals("state", e.getName());
        assertEquals(ConnectionState.CLOSING, e.getValue());
    }

    @Test
    void enumEntry_packedWithNumber_packetHeaderExample() {
        PacketType type = PacketType.DATA;
        int channelId = 22;

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerEnumEntry<>("type", type));
        ser.addEntry(new BitwiseSerializerNumberEntry("channelId", channelId, 5));
        byte[] packed = ser.pack();
        assertEquals(1, packed.length);

        BitwiseSerializerEnumEntry<PacketType> rType = new BitwiseSerializerEnumEntry<>("type", PacketType.class);
        BitwiseSerializerNumberEntry rChannel = new BitwiseSerializerNumberEntry("channelId", 5);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(rType);
        deser.addEntry(rChannel);
        deser.unpack(packed);

        assertEquals(type, rType.getValue());
        assertEquals(channelId, rChannel.intValue());
    }

    @Test
    void flagsEntry_allTrue() {
        BitwiseSerializerFlagsEntry flags = new BitwiseSerializerFlagsEntry("a", "b", "c")
                .set("a", true).set("b", true).set("c", true);

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(flags);
        byte[] packed = ser.pack();

        BitwiseSerializerFlagsEntry r = new BitwiseSerializerFlagsEntry("a", "b", "c");
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertTrue(r.get("a"));
        assertTrue(r.get("b"));
        assertTrue(r.get("c"));
    }

    @Test
    void flagsEntry_allFalse() {
        BitwiseSerializerFlagsEntry flags = new BitwiseSerializerFlagsEntry("x", "y");

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(flags);
        byte[] packed = ser.pack();

        BitwiseSerializerFlagsEntry r = new BitwiseSerializerFlagsEntry("x", "y");
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertFalse(r.get("x"));
        assertFalse(r.get("y"));
    }

    @Test
    void flagsEntry_mixed() {
        BitwiseSerializerFlagsEntry flags = new BitwiseSerializerFlagsEntry("active", "admin", "premium", "canEdit")
                .set("active", true)
                .set("admin", false)
                .set("premium", true)
                .set("canEdit", false);

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(flags);
        byte[] packed = ser.pack();

        BitwiseSerializerFlagsEntry r = new BitwiseSerializerFlagsEntry("active", "admin", "premium", "canEdit");
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertTrue(r.get("active"));
        assertFalse(r.get("admin"));
        assertTrue(r.get("premium"));
        assertFalse(r.get("canEdit"));
    }

    @Test
    void flagsEntry_8flags_fitInOneByte() {
        BitwiseSerializerFlagsEntry flags = new BitwiseSerializerFlagsEntry(
                "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7");
        flags.set("f0", true).set("f2", true).set("f4", true).set("f6", true);

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(flags);
        byte[] packed = ser.pack();
        assertEquals(1, packed.length);

        BitwiseSerializerFlagsEntry r = new BitwiseSerializerFlagsEntry(
                "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7");
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(r);
        deser.unpack(packed);

        assertTrue(r.get("f0"));
        assertFalse(r.get("f1"));
        assertTrue(r.get("f2"));
        assertFalse(r.get("f3"));
        assertTrue(r.get("f4"));
        assertFalse(r.get("f5"));
        assertTrue(r.get("f6"));
        assertFalse(r.get("f7"));
    }

    @Test
    void flagsEntry_unknownFlagThrows() {
        BitwiseSerializerFlagsEntry flags = new BitwiseSerializerFlagsEntry("a", "b");
        assertThrows(IllegalArgumentException.class, () -> flags.set("z", true));
        assertThrows(IllegalArgumentException.class, () -> flags.get("z"));
    }

    @Test
    void flagsEntry_getAll_returnsSnapshot() {
        BitwiseSerializerFlagsEntry flags = new BitwiseSerializerFlagsEntry("on", "off")
                .set("on", true);
        var all = flags.getAll();
        assertEquals(2, all.size());
        assertTrue(all.get("on"));
        assertFalse(all.get("off"));
    }

    @Test
    void flagsEntry_mixedWithEnumAndNumber() {
        PacketType type = PacketType.DATA;
        int channelId = 15;

        BitwiseSerializer ser = new BitwiseSerializer();
        ser.addEntry(new BitwiseSerializerEnumEntry<>(type));
        ser.addEntry(new BitwiseSerializerNumberEntry(channelId, 5));
        ser.addEntry(new BitwiseSerializerFlagsEntry("reliable", "compressed", "encrypted")
                .set("reliable", true)
                .set("encrypted", true));
        byte[] packed = ser.pack();
        assertEquals(2, packed.length);

        BitwiseSerializerEnumEntry<PacketType> rType = new BitwiseSerializerEnumEntry<>(PacketType.class);
        BitwiseSerializerNumberEntry rChannel = new BitwiseSerializerNumberEntry(5);
        BitwiseSerializerFlagsEntry rFlags = new BitwiseSerializerFlagsEntry("reliable", "compressed", "encrypted");
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(rType);
        deser.addEntry(rChannel);
        deser.addEntry(rFlags);
        deser.unpack(packed);

        assertEquals(type, rType.getValue());
        assertEquals(channelId, rChannel.intValue());
        assertTrue(rFlags.get("reliable"));
        assertFalse(rFlags.get("compressed"));
        assertTrue(rFlags.get("encrypted"));
    }

    @Test
    void builder_singleNumberEntry() {
        byte[] packed = BitwiseSerializer.builder()
            .entry()
                .name("test")
                .bits(5)
                .value(21L)
            .build()
            .pack();

        BitwiseSerializerNumberEntry r = new BitwiseSerializerNumberEntry("test", 5);
        new BitwiseSerializer().addEntry(r).unpack(packed);

        assertEquals(21L, r.longValue());
    }

    @Test
    void builder_twoNumberEntries_noNames() {
        byte[] packed = BitwiseSerializer.builder()
            .entry()
                .bits(5)
                .value(22)
            .entry()
                .bits(9)
                .value(300)
            .build()
            .pack();

        BitwiseSerializerNumberEntry rA = new BitwiseSerializerNumberEntry(5);
        BitwiseSerializerNumberEntry rB = new BitwiseSerializerNumberEntry(9);
        new BitwiseSerializer().addEntry(rA).addEntry(rB).unpack(packed);

        assertEquals(22,  rA.intValue());
        assertEquals(300, rB.intValue());
    }

    @Test
    void builder_booleanEntry() {
        byte[] packed = BitwiseSerializer.builder()
            .entry()
                .name("reliable")
                .value(true)
            .entry()
                .name("compressed")
                .value(false)
            .build()
            .pack();

        BitwiseSerializerBooleanEntry rA = new BitwiseSerializerBooleanEntry("reliable");
        BitwiseSerializerBooleanEntry rB = new BitwiseSerializerBooleanEntry("compressed");
        new BitwiseSerializer().addEntry(rA).addEntry(rB).unpack(packed);

        assertTrue(rA.getValue());
        assertFalse(rB.getValue());
    }

    @Test
    void builder_enumEntry() {
        byte[] packed = BitwiseSerializer.builder()
            .entry()
                .name("state")
                .value(ConnectionState.CONNECTED)
            .build()
            .pack();

        BitwiseSerializerEnumEntry<ConnectionState> r =
            new BitwiseSerializerEnumEntry<>("state", ConnectionState.class);
        new BitwiseSerializer().addEntry(r).unpack(packed);

        assertEquals(ConnectionState.CONNECTED, r.getValue());
    }

    @Test
    void builder_flagsEntry() {
        byte[] packed = BitwiseSerializer.builder()
            .entry()
                .flags("reliable", "compressed", "encrypted")
                .flag("reliable",  true)
                .flag("encrypted", true)
            .build()
            .pack();

        BitwiseSerializerFlagsEntry r = new BitwiseSerializerFlagsEntry("reliable", "compressed", "encrypted");
        new BitwiseSerializer().addEntry(r).unpack(packed);

        assertTrue(r.get("reliable"));
        assertFalse(r.get("compressed"));
        assertTrue(r.get("encrypted"));
    }

    @Test
    void builder_fullPacketHeader_allEntryTypes() {
        byte[] packed = BitwiseSerializer.builder()
            .entry()
                .name("type")
                .value(PacketType.ACK)
            .entry()
                .name("channelId")
                .bits(5)
                .value(7)
            .entry()
                .name("highPriority")
                .value(true)
            .entry()
                .name("seqNum")
                .bits(16)
                .value(54321)
            .entry()
                .flags("reliable", "compressed", "encrypted")
                .flag("reliable", true)
            .build()
            .pack();

        assertEquals(4, packed.length);

        BitwiseSerializerEnumEntry<PacketType> rType     = new BitwiseSerializerEnumEntry<>(PacketType.class);
        BitwiseSerializerNumberEntry           rChannel  = new BitwiseSerializerNumberEntry(5);
        BitwiseSerializerBooleanEntry          rPriority = new BitwiseSerializerBooleanEntry();
        BitwiseSerializerNumberEntry           rSeq      = new BitwiseSerializerNumberEntry(16);
        BitwiseSerializerFlagsEntry            rFlags    = new BitwiseSerializerFlagsEntry("reliable", "compressed", "encrypted");

        new BitwiseSerializer()
            .addEntry(rType).addEntry(rChannel).addEntry(rPriority)
            .addEntry(rSeq).addEntry(rFlags)
            .unpack(packed);

        assertEquals(PacketType.ACK, rType.getValue());
        assertEquals(7,     rChannel.intValue());
        assertTrue(rPriority.getValue());
        assertEquals(54321, rSeq.intValue());
        assertTrue(rFlags.get("reliable"));
        assertFalse(rFlags.get("compressed"));
        assertFalse(rFlags.get("encrypted"));
    }

    @Test
    void builder_missingBitsForNumber_throws() {
        assertThrows(IllegalStateException.class, () ->
            BitwiseSerializer.builder()
                .entry()
                    .value(42)
                .build());
    }

    @Test
    void builder_missingValue_throws() {
        assertThrows(IllegalStateException.class, () ->
            BitwiseSerializer.builder()
                .entry()
                    .name("oops")
                    .bits(8)
                .build());
    }

    @Test
    void builder_customEntry_viaEntryBuilderChain() {
        BitwiseSerializerNumberEntry custom = new BitwiseSerializerNumberEntry(7, 4);
        byte[] packed = BitwiseSerializer.builder()
            .entry()
                .custom(custom)
            .build()
            .pack();

        BitwiseSerializerNumberEntry rEntry = new BitwiseSerializerNumberEntry(4);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(rEntry);
        deser.unpack(packed);
        assertEquals(7, rEntry.longValue());
    }

    @Test
    void builder_customEntry_viaBuilderDirect() {
        BitwiseSerializerBooleanEntry custom = new BitwiseSerializerBooleanEntry(true);
        byte[] packed = BitwiseSerializer.builder()
            .entry(custom)
            .build()
            .pack();

        BitwiseSerializerBooleanEntry rEntry = new BitwiseSerializerBooleanEntry();
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(rEntry);
        deser.unpack(packed);
        assertTrue(rEntry.getValue());
    }

    @Test
    void builder_customEntry_mixedWithBuiltIn() {
        BitwiseSerializerFlagsEntry customFlags = new BitwiseSerializerFlagsEntry("urgent", "compressed");
        customFlags.set("urgent", true).set("compressed", false);

        byte[] packed = BitwiseSerializer.builder()
            .entry(customFlags)
            .entry()
                .bits(4)
                .value(9)
            .build()
            .pack();

        BitwiseSerializerFlagsEntry rFlags = new BitwiseSerializerFlagsEntry("urgent", "compressed");
        BitwiseSerializerNumberEntry rNum = new BitwiseSerializerNumberEntry(4);
        BitwiseSerializer deser = new BitwiseSerializer();
        deser.addEntry(rFlags);
        deser.addEntry(rNum);
        deser.unpack(packed);
        assertTrue(rFlags.get("urgent"));
        assertFalse(rFlags.get("compressed"));
        assertEquals(9, rNum.longValue());
    }
}
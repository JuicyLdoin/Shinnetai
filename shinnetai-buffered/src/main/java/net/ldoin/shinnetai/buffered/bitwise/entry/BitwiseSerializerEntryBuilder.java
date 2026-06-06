package net.ldoin.shinnetai.buffered.bitwise.entry;

import net.ldoin.shinnetai.buffered.bitwise.BitwiseSerializer;
import net.ldoin.shinnetai.buffered.bitwise.BitwiseSerializerBuilder;

import java.util.LinkedHashMap;

public class BitwiseSerializerEntryBuilder {

    private final BitwiseSerializerBuilder parent;

    private String name;
    private int bits;
    private Number numberValue;
    private Boolean booleanValue;
    private Enum<?> enumValue;
    private String[] flagNames;
    private final LinkedHashMap<String, Boolean> flagSettings = new LinkedHashMap<>();
    private BitwiseSerializerEntry customEntry;
    private Kind kind = Kind.UNSET;

    public BitwiseSerializerEntryBuilder(BitwiseSerializerBuilder parent) {
        this.parent = parent;
    }

    public BitwiseSerializerEntryBuilder name(String name) {
        this.name = name;
        return this;
    }

    public BitwiseSerializerEntryBuilder bits(int bits) {
        this.bits = bits;
        return this;
    }

    public BitwiseSerializerEntryBuilder value(Number value) {
        this.numberValue = value;
        this.kind = Kind.NUMBER;
        return this;
    }

    public BitwiseSerializerEntryBuilder value(boolean value) {
        this.booleanValue = value;
        this.kind = Kind.BOOLEAN;
        return this;
    }

    public BitwiseSerializerEntryBuilder value(Enum<?> value) {
        this.enumValue = value;
        this.kind = Kind.ENUM;
        return this;
    }

    public BitwiseSerializerEntryBuilder flags(String... flagNames) {
        this.flagNames = flagNames;
        this.kind = Kind.FLAGS;
        return this;
    }

    public BitwiseSerializerEntryBuilder flag(String flagName, boolean value) {
        flagSettings.put(flagName, value);
        return this;
    }

    public BitwiseSerializerEntryBuilder custom(BitwiseSerializerEntry entry) {
        this.customEntry = entry;
        this.kind = Kind.CUSTOM;
        return this;
    }

    public BitwiseSerializerEntryBuilder entry() {
        parent.commit(buildEntry());
        return new BitwiseSerializerEntryBuilder(parent);
    }

    public BitwiseSerializer build() {
        parent.commit(buildEntry());
        return parent.build();
    }

    private BitwiseSerializerEntry buildEntry() {
        return switch (kind) {
            case NUMBER -> buildNumber();
            case BOOLEAN -> buildBoolean();
            case ENUM -> buildEnum();
            case FLAGS -> buildFlags();
            case CUSTOM -> customEntry;
            case UNSET -> throw new IllegalStateException("Entry '" + name + "' has no value configured — call value(), flags(), custom(), etc.");
        };
    }

    private BitwiseSerializerEntry buildNumber() {
        if (bits <= 0) {
            throw new IllegalStateException("Entry '" + name + "': bits() must be set to a positive value for a number entry");
        }

        return name != null
                ? new BitwiseSerializerNumberEntry(name, numberValue, bits)
                : new BitwiseSerializerNumberEntry(numberValue, bits);
    }

    private BitwiseSerializerEntry buildBoolean() {
        return name != null
                ? new BitwiseSerializerBooleanEntry(name, booleanValue)
                : new BitwiseSerializerBooleanEntry(booleanValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BitwiseSerializerEntry buildEnum() {
        return name != null
                ? new BitwiseSerializerEnumEntry(name, enumValue)
                : new BitwiseSerializerEnumEntry(enumValue);
    }

    private BitwiseSerializerEntry buildFlags() {
        if (flagNames == null || flagNames.length == 0) {
            throw new IllegalStateException("Entry '" + name + "': flags() must declare at least one flag name");
        }

        BitwiseSerializerFlagsEntry entry = new BitwiseSerializerFlagsEntry(flagNames);
        flagSettings.forEach(entry::set);
        return entry;
    }

    private enum Kind {

        UNSET,
        NUMBER,
        BOOLEAN,
        ENUM,
        FLAGS,
        CUSTOM

    }
}

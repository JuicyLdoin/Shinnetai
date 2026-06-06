package net.ldoin.shinnetai.serializer;

import com.google.flatbuffers.ArrayReadWriteBuf;
import com.google.flatbuffers.FlexBuffers;
import com.google.flatbuffers.FlexBuffersBuilder;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class FlexBuffersPacketSerializer implements PacketSerializer {

    public static final FlexBuffersPacketSerializer INSTANCE = new FlexBuffersPacketSerializer();

    private static final int INITIAL_BUFFER_SIZE = 512;
    private static final ConcurrentHashMap<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private static List<Field> collectFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, FlexBuffersPacketSerializer::buildFieldList);
    }

    private FlexBuffersPacketSerializer() {
    }

    @Override
    public String name() {
        return "flexbuffers";
    }

    @Override
    public byte[] serialize(AbstractPacket<?, ?> packet) throws IOException {
        FlexBuffersBuilder builder = new FlexBuffersBuilder(
                new ArrayReadWriteBuf(INITIAL_BUFFER_SIZE),
                FlexBuffersBuilder.BUILDER_FLAG_SHARE_KEYS_AND_STRINGS
        );
        writeObject(builder, packet);

        ArrayReadWriteBuf buf = (ArrayReadWriteBuf) builder.getBuffer();
        return Arrays.copyOf(buf.data(), buf.writePosition());
    }

    private void writeObject(FlexBuffersBuilder builder, Object obj) {
        int mapHandle = builder.startMap();
        for (Field field : collectFields(obj.getClass())) {
            try {
                writeField(builder, field.getName(), field.get(obj));
            } catch (IllegalAccessException ignored) {
            }
        }

        builder.endMap(null, mapHandle);
    }

    private void writeField(FlexBuffersBuilder builder, String name, Object value) {
        if (value == null) {
            builder.putNull(name);
            return;
        }

        Class<?> type = value.getClass();
        if (type == Boolean.class) {
            builder.putBoolean(name, (Boolean) value);
        } else if (type == Byte.class) {
            builder.putInt(name, (Byte) value);
        } else if (type == Short.class) {
            builder.putInt(name, (Short) value);
        } else if (type == Integer.class) {
            builder.putInt(name, (Integer) value);
        } else if (type == Long.class) {
            builder.putInt(name, (Long) value);
        } else if (type == Float.class) {
            builder.putFloat(name, (Float) value);
        } else if (type == Double.class) {
            builder.putFloat(name, (Double) value);
        } else if (type == Character.class) {
            builder.putString(name, String.valueOf(value));
        } else if (type == String.class) {
            builder.putString(name, (String) value);
        } else if (type.isEnum()) {
            builder.putString(name, ((Enum<?>) value).name());
        } else if (type.isArray()) {
            writeArray(builder, name, value);
        } else if (value instanceof Iterable<?> iterable) {
            writeIterable(builder, name, iterable);
        } else {
            int nested = builder.startMap();
            List<Field> fields = collectFields(type);
            for (Field field : fields) {
                try {
                    writeField(builder, field.getName(), field.get(value));
                } catch (IllegalAccessException ignored) {
                }
            }

            builder.endMap(name, nested);
        }
    }

    private void writeArray(FlexBuffersBuilder builder, String name, Object array) {
        int len = Array.getLength(array);
        int vec = builder.startVector();
        for (int i = 0; i < len; i++) {
            writeField(builder, null, Array.get(array, i));
        }

        builder.endVector(name, vec, false, false);
    }

    private void writeIterable(FlexBuffersBuilder builder, String name, Iterable<?> iterable) {
        int vec = builder.startVector();
        for (Object element : iterable) {
            writeField(builder, null, element);
        }

        builder.endVector(name, vec, false, false);
    }

    @Override
    public void deserialize(AbstractPacket<?, ?> packet, byte[] data, int offset, int length) throws IOException {
        ArrayReadWriteBuf buf = new ArrayReadWriteBuf(data, offset);
        FlexBuffers.Reference root = FlexBuffers.getRoot(buf);
        if (root.getType() != FlexBuffers.FBT_MAP) {
            throw new IOException("FlexBuffers root is not a map");
        }

        readObject(root.asMap(), packet);
    }

    private void readObject(FlexBuffers.Map map, Object target) {
        List<Field> fields = collectFields(target.getClass());
        for (Field field : fields) {
            FlexBuffers.Reference ref = map.get(field.getName());
            if (ref.isNull()) {
                continue;
            }

            try {
                Object value = readValue(ref, field.getType(), field.get(target));
                field.set(target, value);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object readValue(FlexBuffers.Reference ref, Class<?> targetType, Object existing) throws ReflectiveOperationException {
        if (ref.isNull()) {
            return null;
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            return ref.asBoolean();
        } else if (targetType == byte.class || targetType == Byte.class) {
            return (byte) ref.asInt();
        } else if (targetType == short.class || targetType == Short.class) {
            return (short) ref.asInt();
        } else if (targetType == int.class || targetType == Integer.class) {
            return ref.asInt();
        } else if (targetType == long.class || targetType == Long.class) {
            return ref.asLong();
        } else if (targetType == float.class || targetType == Float.class) {
            return (float) ref.asFloat();
        } else if (targetType == double.class || targetType == Double.class) {
            return ref.asFloat();
        } else if (targetType == char.class || targetType == Character.class) {
            String s = ref.asString();
            return s != null && !s.isEmpty() ? s.charAt(0) : '\0';
        } else if (targetType == String.class) {
            return ref.asString();
        } else if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, ref.asString());
        } else if (targetType.isArray()) {
            return readArray(ref.asVector(), targetType.getComponentType());
        } else if (ref.getType() == FlexBuffers.FBT_MAP) {
            Object obj = existing != null ? existing : targetType.getDeclaredConstructor().newInstance();
            readObject(ref.asMap(), obj);
            return obj;
        }

        return null;
    }

    private Object readArray(FlexBuffers.Vector vec, Class<?> componentType) throws ReflectiveOperationException {
        int len = vec.size();
        Object array = Array.newInstance(componentType, len);
        for (int i = 0; i < len; i++) {
            Array.set(array, i, readValue(vec.get(i), componentType, null));
        }

        return array;
    }

    private static List<Field> buildFieldList(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                    continue;
                }

                String name = f.getName();
                if (name.equals("clientWorker") || name.equals("serverWorker")) {
                    continue;
                }

                try {
                    f.setAccessible(true);
                } catch (RuntimeException ignored) {
                    continue;
                }

                fields.add(f);
            }
        }

        return fields;
    }
}
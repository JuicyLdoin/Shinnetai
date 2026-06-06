package net.ldoin.shinnetai.buffered;

import net.ldoin.shinnetai.buffered.map.ByteMap;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ByteMapTest {

    @Test
    void put_getString() {
        ByteMap map = ByteMap.empty();
        map.put("key", "value");
        assertEquals("value", map.getString("key"));
    }

    @Test
    void put_getInt() {
        ByteMap map = ByteMap.empty();
        map.put("age", 25);
        assertEquals(25, map.getInt("age"));
    }

    @Test
    void put_getLong() {
        ByteMap map = ByteMap.empty();
        map.put("timestamp", 1234567890123L);
        assertEquals(1234567890123L, map.getLong("timestamp"));
    }

    @Test
    void put_getBoolean() {
        ByteMap map = ByteMap.empty();
        map.put("flag", true);
        assertTrue(map.getBoolean("flag"));
    }

    @Test
    void put_getFloat() {
        ByteMap map = ByteMap.empty();
        map.put("pi", 3.14f);
        assertEquals(3.14f, map.getFloat("pi"), 0.0001f);
    }

    @Test
    void put_getDouble() {
        ByteMap map = ByteMap.empty();
        map.put("e", Math.E);
        assertEquals(Math.E, map.getDouble("e"), 0.000001);
    }

    @Test
    void put_getUUID() {
        ByteMap map = ByteMap.empty();
        UUID id = UUID.randomUUID();
        map.put("id", id);
        assertEquals(id, map.getUUID("id"));
    }

    @Test
    void serialization_roundtrip_string() {
        ByteMap original = ByteMap.empty();
        original.put("name", "Shinnetai");
        original.put("version", 1);

        byte[] bytes = original.toBytes();
        ByteMap restored = ByteMap.of(bytes);

        assertEquals("Shinnetai", restored.getString("name"));
        assertEquals(1, restored.getInt("version"));
    }

    @Test
    void serialization_roundtrip_multipleTypes() {
        UUID id = UUID.randomUUID();
        ByteMap original = ByteMap.empty();
        original.put("str", "hello");
        original.put("num", 42);
        original.put("flag", false);
        original.put("uuid", id);

        ByteMap restored = ByteMap.of(original.toBytes());

        assertEquals("hello", restored.getString("str"));
        assertEquals(42, restored.getInt("num"));
        assertFalse(restored.getBoolean("flag"));
        assertEquals(id, restored.getUUID("uuid"));
    }

    @Test
    void empty_map_serializes_and_deserializes() {
        ByteMap original = ByteMap.empty();
        byte[] bytes = original.toBytes();
        ByteMap restored = ByteMap.of(bytes);
        assertTrue(restored.isEmpty());
    }

    @Test
    void put_overwritesExistingKey() {
        ByteMap map = ByteMap.empty();
        map.put("key", "first");
        map.put("key", "second");
        assertEquals("second", map.getString("key"));
    }

    @Test
    void size_reflectsEntries() {
        ByteMap map = ByteMap.empty();
        assertEquals(0, map.size());
        map.put("a", 1);
        map.put("b", 2);
        assertEquals(2, map.size());
    }
}

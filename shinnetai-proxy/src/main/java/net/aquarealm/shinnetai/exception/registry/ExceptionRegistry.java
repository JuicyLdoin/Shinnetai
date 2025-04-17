package net.aquarealm.shinnetai.exception.registry;

import net.aquarealm.shinnetai.exception.ShinnetaiException;
import net.aquarealm.shinnetai.exception.ShinnetaiExceptions;

import java.util.HashMap;
import java.util.Map;

public class ExceptionRegistry {

    private static final Map<Integer, ShinnetaiException> REGISTRY = new HashMap<>();

    static {
        register(ShinnetaiExceptions.values());
    }

    public static ShinnetaiException getException(int id) {
        return REGISTRY.get(id);
    }

    public static String getMessage(int id, Object... objects) {
        return String.format(REGISTRY.get(id).getMessage(), objects);
    }

    public static String getMessage(ShinnetaiException exception, Object... objects) {
        return String.format(exception.getMessage(), objects);
    }

    public static void register(ShinnetaiException exception) {
        REGISTRY.put(exception.getId(), exception);
    }

    public static void register(ShinnetaiException... exceptions) {
        for (ShinnetaiException exception : exceptions) {
            REGISTRY.put(exception.getId(), exception);
        }
    }
}
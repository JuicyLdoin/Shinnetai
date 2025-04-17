package net.aquarealm.shinnetai.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@SuppressWarnings("unused")
public class ReflectionUtil {

    @SuppressWarnings("unchecked")
    public static <T> List<Class<? extends T>> getClassesImplement(String packageName, Class<T> clazz) {
        List<Class<? extends T>> result = new ArrayList<>();
        List<Class<?>> allClasses = getClassesInPackage(packageName);
        for (Class<?> candidateClass : allClasses) {
            if (clazz.isAssignableFrom(candidateClass) && !java.lang.reflect.Modifier.isAbstract(candidateClass.getModifiers())) {
                result.add((Class<? extends T>) candidateClass);
            }
        }

        return result;
    }

    public static List<Class<?>> getClassesInPackage(String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            String path = packageName.replace('.', '/');
            ClassLoader classLoader = ReflectionUtil.class.getClassLoader();
            Enumeration<URL> resources = classLoader.getResources(path);

            int size = 0;
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String file = resource.getFile().replace("file:", "").replace("\\", "/");
                String protocol = resource.getProtocol();
                if (protocol.equals("file")) {
                    File directory = new File(file);
                    findClassesInDirectory(packageName, directory, classes);
                } else if (protocol.equals("jar")) {
                    int exclamationIndex = file.indexOf("!");
                    if (exclamationIndex != -1) {
                        String jarFilePath = file.substring(0, exclamationIndex);
                        File jarFile = new File(jarFilePath);
                        findClassesInJar(packageName, jarFile, classes);
                    }
                }
                size++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load classes from package: " + packageName, e);
        }

        return classes;
    }

    private static void findClassesInDirectory(String packageName, File directory, List<Class<?>> classes) {
        if (!directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                findClassesInDirectory(packageName + "." + file.getName(), file, classes);
            } else if (file.getName().endsWith(".class")) {
                String className = file.getName().substring(0, file.getName().length() - 6);
                try {
                    classes.add(Class.forName(packageName + "." + className));
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Failed to load class: " + className, e);
                }
            }
        }
    }

    private static void findClassesInJar(String packageName, File jarFile, List<Class<?>> classes) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            String packagePath = packageName.replace('.', '/');
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                    String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
                    try {
                        classes.add(Class.forName(className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Failed to load class: " + className, e);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
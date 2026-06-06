package net.ldoin.shinnetai.util;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ReflectionUtil {

    @SuppressWarnings("unchecked")
    public static <T> List<Class<? extends T>> getClassesImplement(String packageName, Class<T> clazz) {
        List<Class<? extends T>> result = new ArrayList<>();
        List<Class<?>> allClasses = getClassesInPackage(packageName);
        for (Class<?> candidateClass : allClasses) {
            if (clazz.isAssignableFrom(candidateClass) && !Modifier.isAbstract(candidateClass.getModifiers())) {
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
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();
                if (protocol.equals("file")) {
                    Path directory;
                    try {
                        directory = Path.of(resource.toURI());
                    } catch (java.net.URISyntaxException e) {
                        directory = Path.of(resource.getFile());
                    }

                    findClassesInDirectory(packageName, directory, classes);
                } else if (protocol.equals("jar")) {
                    String file = resource.getFile();
                    try {
                        file = new java.net.URI(file).getSchemeSpecificPart();
                    } catch (java.net.URISyntaxException e) {
                        file = file.replace("file:", "").replace("\\", "/");
                    }

                    int exclamationIndex = file.indexOf("!");
                    if (exclamationIndex != -1) {
                        String jarFilePath = file.substring(0, exclamationIndex);
                        findClassesInJar(packageName, Path.of(jarFilePath), classes);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load classes from package: " + packageName, e);
        }

        return classes;
    }

    private static void findClassesInDirectory(String packageName, Path directory, List<Class<?>> classes) {
        if (!Files.exists(directory)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (Files.isDirectory(file)) {
                    findClassesInDirectory(packageName + "." + file.getFileName().toString(), file, classes);
                } else if (file.getFileName().toString().endsWith(".class")) {
                    String name = file.getFileName().toString();
                    String className = name.substring(0, name.length() - 6);
                    if (!className.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                        continue;
                    }

                    try {
                        classes.add(Class.forName(packageName + "." + className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Failed to load class: " + className, e);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list directory: " + directory, e);
        }
    }

    private static void findClassesInJar(String packageName, Path jarFilePath, List<Class<?>> classes) {
        try (JarFile jar = new JarFile(jarFilePath.toString())) {
            Enumeration<JarEntry> entries = jar.entries();
            String packagePath = packageName.replace('.', '/');
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                    String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
                    boolean valid = !className.isEmpty() && className.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '$');
                    if (!valid) {
                        continue;
                    }
                    
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
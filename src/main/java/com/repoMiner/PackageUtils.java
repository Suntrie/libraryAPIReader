package com.repoMiner;

import org.jetbrains.annotations.NotNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class PackageUtils {

    private static class CustomClassLoader extends ClassLoader {

        private JarFile jarFile;

        public CustomClassLoader(JarFile jarFile){
            this.jarFile=jarFile;
        }

        private Map<String, Class> loadedClasses = new HashMap<>();

        private boolean parentClassLoaderAccess = false;

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {

            String pointedFQN = getPointedFQN(name);

            if (loadedClasses.keySet().contains(pointedFQN))
                return loadedClasses.get(pointedFQN);

            if (name.contains("java.") || name.contains("javax") || parentClassLoaderAccess) {

                loadedClasses.put(pointedFQN, super.loadClass(pointedFQN, true));

                if (parentClassLoaderAccess)
                    parentClassLoaderAccess = false;

                return loadedClasses.get(pointedFQN);
            } else {

                byte b[] = new byte[0];

                String pathFQN = getPathFQN(name);

                try {
                    b = fetchClassFromFS(pathFQN);
                    if (b == null) {
                        parentClassLoaderAccess = true;
                        return loadClass(pathFQN);
                    }
                } catch (IOException e) {
                    throw new ClassNotFoundException("IOException");
                }

                Class definedClass = defineClass(pointedFQN, b, 0, b.length);
                this.resolveClass(definedClass);

                loadedClasses.put(pointedFQN,
                        definedClass);

                return definedClass;
            }
        }

        @NotNull
        private String getPathFQN(String name) {
            if ((name.contains(".")) && (!name.endsWith(".class"))) {
                name = name.
                        replaceAll("\\.", "/");
                name = name.concat(".class");
            }
            return name;
        }

        @NotNull
        private String getPointedFQN(String name) {
            String fullQualifiedClassName = name.
                    replaceAll("/", "\\.");

            if (fullQualifiedClassName.endsWith(".class"))
                fullQualifiedClassName = fullQualifiedClassName.
                        substring(0, name.lastIndexOf("."));
            return fullQualifiedClassName;
        }

        private byte[] fetchClassFromFS(String name) throws IOException {

            if (jarFile.getJarEntry(name) == null)
                return null;

            InputStream jarInputStream = jarFile
                    .getInputStream(jarFile.getJarEntry(name));

            byte[] bytes = getBytes(jarInputStream);

            jarInputStream.close();
            return bytes;

        }

        private byte[] getBytes(InputStream is) throws IOException {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream();) {
                byte[] buffer = new byte[0xFFFF];
                for (int len; (len = is.read(buffer)) != -1; )
                    os.write(buffer, 0, len);
                os.flush();
                return os.toByteArray();
            }
        }
    }

    private static JarEntry getNextJarEntryMatches(Enumeration<JarEntry> jarEntryEnumeration,
                                                   String template) throws IOException {
        JarEntry jarEntry = null;

        while (true) {

            if (!jarEntryEnumeration.hasMoreElements()) {

                jarEntry = null;
                break;
            }
            jarEntry = jarEntryEnumeration.nextElement();
            if ((jarEntry == null) || (jarEntry.getName().endsWith(template))) {
                break;
            }
        }

        return jarEntry;
    }


    /*@jarPath - path to base libraries dir
     * */
    public static Set<Class> getLibraryClassSet(String jarPath)
            throws IOException, ClassNotFoundException {
        Set<Class> classes = new HashSet<Class>();

        JarFile jarFile = new JarFile(jarPath);
        JarEntry jarEntry;

        Enumeration<JarEntry> jarEntryEnumeration = jarFile.entries();

        CustomClassLoader customClassLoader=new CustomClassLoader(jarFile);
        while (true) {

            jarEntry = getNextJarEntryMatches(jarEntryEnumeration, ".class");

            if (jarEntry == null) {
                break;
            }

            Class clazz = null;

            clazz = customClassLoader.
                    loadClass(jarEntry.getName());  //sic - анонимные классы и вложенные интерфейсы будут давать null
            classes.add(clazz);

        }

        return classes;
    }

    public static Set<Method> getExecutableLibraryMethods(String jarPath)
            throws IOException,
            ClassNotFoundException {

        Set<Method> executableMethods = new HashSet<Method>();
        Set<Class> libraryAPIClasses = getLibraryClassSet(jarPath);

        for (Class libraryAPIClass : libraryAPIClasses) {
                if (!Modifier.isAbstract(libraryAPIClass.getModifiers())) {
                    executableMethods.addAll(Arrays.stream(libraryAPIClass.getMethods())
                            .filter(it -> Modifier.isPublic(it.getModifiers())).collect(Collectors.toSet()));
                }
        }
        return executableMethods;
    }

}
package com.repoMiner;

import kotlin.Pair;
import kotlin.Triple;
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

public class CustomClassLoader extends ClassLoader {

    private Set<JarFile> jarFiles=new HashSet<>();

    private JarFile currentlyLoadingJar;

    private Map<String, Class> loadedClasses = new HashMap<>();

    private boolean parentClassLoaderAccess = false;

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {

        String pointedFQN = getPointedFQN(name);

        if (loadedClasses.keySet().contains(pointedFQN))
            return loadedClasses.get(pointedFQN);

        if (name.contains("java.") || parentClassLoaderAccess) {

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
    private static String getPathFQN(String name) {
        if ((name.contains(".")) && (!name.endsWith(".class"))) {
            name = name.
                    replaceAll("\\.", "/");
            name = name.concat(".class");
        }
        return name;
    }

    @NotNull
    private static String getPointedFQN(String name) {
        String fullQualifiedClassName = name.
                replaceAll("/", "\\.");

        if (fullQualifiedClassName.endsWith(".class"))
            fullQualifiedClassName = fullQualifiedClassName.
                    substring(0, name.lastIndexOf("."));
        return fullQualifiedClassName;
    }

    private byte[] fetchClassFromFS(String name) throws IOException {

        if (currentlyLoadingJar.getJarEntry(name) == null)
            return null;

        InputStream jarInputStream = currentlyLoadingJar
                .getInputStream(currentlyLoadingJar.getJarEntry(name));

        byte[] bytes = getBytes(jarInputStream);

        jarInputStream.close();
        return bytes;

    }

    private static byte[] getBytes(InputStream is) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();) {
            byte[] buffer = new byte[0xFFFF];
            for (int len; (len = is.read(buffer)) != -1; )
                os.write(buffer, 0, len);
            os.flush();
            return os.toByteArray();
        }
    }

    public static JarEntry getNextJarEntryMatches( Enumeration<JarEntry> jarEntryEnumeration,
                                                   String template) {
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
    // first - set of loaded classes
    // second - set of missed classes
    public Pair<Set<Class>, Set<String>> loadLibraryClassSet(String jarPath, Set<String> filterClassNames)
            throws IOException {

        Set<Class> loadedClasses = new HashSet<Class>();
        Set<String> missedClassNames = new HashSet<String>();

        currentlyLoadingJar = new JarFile(jarPath);
        JarEntry jarEntry;

        Enumeration<JarEntry> jarEntryEnumeration = currentlyLoadingJar.entries();

        while (true) {

            jarEntry = getNextJarEntryMatches(jarEntryEnumeration, ".class");

            if (jarEntry == null) {
                break;
            }

            boolean filter=false;

            for(String filterClassName: filterClassNames) {
                if (jarEntry.getName().matches(filterClassName))
                    filter=true;
            };

            if (filter) continue;

            Class clazz = null;

            try {
                clazz = this.
                        loadClass(jarEntry.getName());  //sic - анонимные классы и вложенные интерфейсы будут давать null
            }catch (ClassNotFoundException | NoClassDefFoundError e){ // NoClassDefFoundError - класс был доступен при компиляции, но не доступен в runtime
                missedClassNames.add(jarEntry.getName());             // например, потому что мы не грузим чужие опциональные зависимости

            }

            if (clazz!=null)
                loadedClasses.add(clazz);
        }

        jarFiles.add(currentlyLoadingJar);

        return new Pair<>(loadedClasses, missedClassNames);
    }

    public Set<String> getMissedClassNames(String jarPath, Set<String> filterClassNames) throws IOException {
        return loadLibraryClassSet(jarPath, filterClassNames).getSecond();
    }

    public Triple<Set<Method>, Set<Class>, Set<Class>> getExecutableLibraryMethods(String jarPath,
                                                                                   Set<String> filterClassNames)
            throws IOException {

        Set<Method> executableMethods = new HashSet<Method>();

        Set<Class> definedClasses=new HashSet<>();
        Set<Class> missedMethodDef=new HashSet<>();

        for (Class libraryAPIClass : loadLibraryClassSet(jarPath, filterClassNames).getFirst()) {
            if (!Modifier.isAbstract(libraryAPIClass.getModifiers())) {
                try {
                    executableMethods.addAll(Arrays.stream(libraryAPIClass.getMethods())
                            .filter(it -> Modifier.isPublic(it.getModifiers())).collect(Collectors.toSet()));
                    definedClasses.add(libraryAPIClass);
                }catch (NoClassDefFoundError e){
                    missedMethodDef.add(libraryAPIClass);
                }
            }
        }

        //executableMethods.stream().forEach(it-> System.out.println(it.getName()));

        return new Triple<>(executableMethods, definedClasses, missedMethodDef);
    }

}
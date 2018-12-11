package com.repoMiner

import java.net.URL
import java.util.*

fun main(args: Array<String>) {


    val jarURLs = ArrayList<URL>()

    jarURLs.add(URL("file:///"+
            /*"/E:/testLib/out/artifacts/testLib_jar/testLib.jar"*/"/C:/Users/Neverland/.m2/repository/com/netflix/spectator/spectator-api/0.57.1/spectator-api-0.57.1.jar"))


    val customClassLoader=CustomClassLoader();

    for (jarURL in jarURLs) {
        customClassLoader.loadLibraryClassSet(jarURL.path, setOf());
        customClassLoader.getExecutableLibraryMethods(jarURL.getPath(), setOf())
    }


}
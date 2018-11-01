package com.repoMiner

import com.repoMiner.PackageUtils.getExecutableLibraryMethods
import java.net.URL
import java.util.*

fun main(args: Array<String>) {

    val jarURLs = ArrayList<URL>()

    jarURLs.add(URL("file:///"+
            /*"/E:/testLib/out/artifacts/testLib_jar/testLib.jar"*/"/C:/Users/Neverland/.m2/repository/com/netflix/spectator/spectator-api/0.57.1/spectator-api-0.57.1.jar"))

    for (jarURL in jarURLs) {
        getExecutableLibraryMethods(jarURL.getPath())
    }


}
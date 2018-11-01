package com.repoMiner

data class MavenGAV(val groupId: String,
                    val artifactId: String,
                    val versionId: String) {
    companion object {

        var repositoryPath = "C:\\Users\\Neverland\\.m2\\repository"

        fun fromMavenNameToGAV(libraryGAVName: String)=
                MavenGAV( libraryGAVName.substring(0, libraryGAVName.indexOf(":")),
                        libraryGAVName.substring(libraryGAVName.indexOf(":") + 1,
                                libraryGAVName.lastIndexOf(":")),
                        libraryGAVName.substring(libraryGAVName.lastIndexOf(":") + 1))
    }

    fun fromGAVtoPath(): String {
        val result = mutableListOf<String>()
        result.add(repositoryPath)
        result.addAll(groupId.split(".").toMutableList())
        result.add(artifactId)
        result.add(versionId)
        return result.joinToString(separator = "\\")
    }

    fun fromGAVtoJar() = fromGAVtoPath()+"\\"+artifactId + "-" + versionId + ".jar"
}
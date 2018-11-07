package com.cantalou.gradle.incremental.tasks

import com.cantalou.gradle.incremental.ThreadProfile
import org.gradle.api.Project

import java.util.concurrent.*

class FileMonitor {

    static final String lineSeparator = ";"

    HashMap<String, String> resourcesLastModifiedMap = new HashMap<>(128)

    Map<String, String> newResourcesLastModifiedMap = new ConcurrentHashMap<>(128)

    Project project

    File output

    File resourcesLastModifiedFile

    boolean isCleanCheck

    FileMonitor(Project project, String outputName) {
        this.project = project

        output = new File(project.buildDir, "intermediates/${outputName}")
        output.mkdirs()

        resourcesLastModifiedFile = new File(output, "resourcesLastModified.txt")
        Thread.start {
            if (!resourcesLastModifiedFile.exists()) {
                return
            }
            resourcesLastModifiedFile.eachLine("UTF-8") { String line ->
                int firstIndex = line.indexOf(lineSeparator)
                resourcesLastModifiedMap.put(line.substring(0, firstIndex), line)
                isCleanCheck = resourcesLastModifiedMap.isEmpty()
            }
        }
    }

    synchronized void detectModified(Collection<File> files, boolean profile) {

        if (files == null || files.isEmpty()) {
            return
        }

        long start = System.currentTimeMillis()
        project.println "FileMonitor: Start to check java resources modified"
        List<File> javaResourcesDir = new CopyOnWriteArrayList<>(files)

        def threadProfile = new ThreadProfile(project)
        ThreadProfile.Info info = threadProfile.loadInfo()

        ExecutorService service = Executors.newFixedThreadPool(info.threadSize)
        while (!javaResourcesDir.isEmpty()) {
            final File file = javaResourcesDir.remove(0)
            service.execute(new Runnable() {
                @Override
                void run() {
                    if (file.isDirectory()) {
                        File[] subFile = file.listFiles()
                        if (subFile == null) {
                            return
                        }
                        javaResourcesDir.addAll(subFile)
                    } else {
                        String infoStr = resourcesLastModifiedMap.get(file.absolutePath)
                        if (isModified(file, infoStr)) {
                            if (!isCleanCheck) {
                                project.println "FileMonitor: Detect file modified ${file}"
                            }
                            newResourcesLastModifiedMap.put(file.absolutePath, file.absolutePath + lineSeparator + file.lastModified() + lineSeparator + uniqueId(file))
                        }
                    }
                }
            })
        }
        service.awaitTermination(50, TimeUnit.MILLISECONDS)
        int duration = System.currentTimeMillis() - start
        if (profile) {
            threadProfile.updateProfile(duration)
        }
        project.println "FileMonitor: Check java resources modified finish, size:${newResourcesLastModifiedMap.size()}, time:${duration}ms"
    }

    List<File> getModifiedFile() {
        newResourcesLastModifiedMap.keySet().asList()
    }

    boolean isModified(File file, String infoStr) {
        if (infoStr == null || infoStr.length() == 0) {
            if (!isCleanCheck) {
                project.println "infoStr empty"
            }
            return true
        }

        String[] infos = infoStr.split(lineSeparator)
        long fileModified = file.lastModified()
        if (fileModified == Long.parseLong(infos[1])) {
            return false
        }

        int uniqueId = uniqueId(file)
        if (uniqueId == Integer.parseInt(infos[2])) {
            return false
        }

        if (!isCleanCheck) {
            project.println "FileMonitor: infoStr lastModified ${infos[1]},length:${infos[2]}"
            project.println "FileMonitor: file    lastModified ${fileModified}, hashcode:${uniqueId}"
        }
        return true
    }

    void updateResourcesModified() {

        if (newResourcesLastModifiedMap.isEmpty()) {
            return
        }
        resourcesLastModifiedMap.putAll(newResourcesLastModifiedMap)
        resourcesLastModifiedFile.withWriter("UTF-8") { Writer writer ->
            resourcesLastModifiedMap.each { k, v ->
                writer.println(v)
            }
        }
        project.println "FileMonitor: Update java resources modified info"
    }


    int uniqueId(File file) {
        file.getText("UTF-8").hashCode()
    }

    void clearCache() {
        resourcesLastModifiedFile.delete()
    }
}
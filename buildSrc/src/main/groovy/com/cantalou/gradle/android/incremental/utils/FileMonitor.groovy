package com.cantalou.gradle.android.incremental.utils

import com.cantalou.gradle.android.incremental.IncrementalBuildPlugin
import org.gradle.api.Project

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class FileMonitor {

    static final String lineSeparator = ";"

    HashMap<String, String> resourcesLastModifiedMap = new HashMap<>(128)

    Map<String, String> newResourcesLastModifiedMap = new ConcurrentHashMap<>(128)

    Project project

    File outputDIr

    File resourcesLastModifiedFile

    FileMonitor(Project project, File outputDir) {
        this.project = project
        this.outputDIr = outputDir
        Thread.start {
            init()
        }
    }

    synchronized void init() {
        outputDIr.mkdirs()
        resourcesLastModifiedFile = new File(outputDIr, "resourcesLastModified.txt")
        if (!resourcesLastModifiedFile.exists()) {
            return
        }
        resourcesLastModifiedFile.eachLine("UTF-8") { String line ->
            int firstIndex = line.indexOf(lineSeparator)
            resourcesLastModifiedMap.put(line.substring(0, firstIndex), line)
        }
    }

    synchronized List<File> detectModified(Collection<File> files) {

        if (files == null || files.isEmpty()) {
            return Collections.emptyList()
        }

        int currentSize = newResourcesLastModifiedMap.size()
        long start = System.currentTimeMillis()
        AtomicInteger tasks = new AtomicInteger(0)
        ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 64)
        files.each { File sourceFile ->
            addDetectTask(service, sourceFile, tasks)
        }
        while (tasks.get() > 0) {
            project.println("FileMonitor: task size ${tasks.get()}, wait 50ms")
            Thread.sleep(50)
        }
        service.shutdown()

        int duration = System.currentTimeMillis() - start
        if (IncrementalBuildPlugin.loggable) {
            project.println("FileMonitor: Check resources modified finish, size:${newResourcesLastModifiedMap.size() - currentSize}, time:${duration}ms")
        }

        List<File> result = new ArrayList<>()
        files.each { File sourceFile ->
            if (newResourcesLastModifiedMap.containsKey(sourceFile.absolutePath)) {
                result << sourceFile
            }
        }
        return result
    }

    void addDetectTask(ExecutorService service, File file, AtomicInteger tasks) {
        tasks.incrementAndGet()
        service.execute(new Runnable() {
            @Override
            void run() {
                if (file.isDirectory()) {
                    File[] subFiles = file.listFiles()
                    if (subFiles == null) {
                        return
                    }
                    subFiles.each { File subFile ->
                        addDetectTask(service, subFile, tasks)
                    }

                } else {
                    detectModified(file)
                }
                tasks.decrementAndGet()
            }
        })
    }

    boolean detectModified(File file) {
        if (!file.exists()) {
            return false
        }
        String infoStr = resourcesLastModifiedMap.get(file.absolutePath)
        if (isModified(file, infoStr)) {
            if (IncrementalBuildPlugin.loggable) {
                project.println("FileMonitor: Detect file modified ${file}")
            }
            newResourcesLastModifiedMap.put(file.absolutePath, file.absolutePath + lineSeparator + file.lastModified() + lineSeparator + uniqueId(file))
            return true
        }
        return false
    }

    List<String> getModifiedFile() {
        newResourcesLastModifiedMap.keySet().asList()
    }

    boolean isModified(File file, String infoStr) {
        if (infoStr == null || infoStr.length() == 0) {
            if (IncrementalBuildPlugin.loggable) {
                project.println("infoStr empty")
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

        if (IncrementalBuildPlugin.loggable) {
            project.println("FileMonitor: infoStr lastModified ${infos[1]}, uniqueId:${infos[2]}")
            project.println("FileMonitor: file    lastModified ${fileModified}, uniqueId:${uniqueId}")
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
        if (IncrementalBuildPlugin.loggable) {
            project.println("FileMonitor: Update resources modified info")
        }
    }


    int uniqueId(File file) {
        return Math.abs(file.getText("UTF-8").hashCode())
    }

    void clearCache() {
        resourcesLastModifiedFile.delete()
    }

}
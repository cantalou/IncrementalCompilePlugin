package com.cantalou.gradle.android.incremental.utils

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.util.concurrent.*

class FileMonitor {

    private static final Logger LOG = Logging.getLogger(FileMonitor.class)

    static final String lineSeparator = ";"

    HashMap<String, String> resourcesLastModifiedMap = new HashMap<>(128)

    Map<String, String> newResourcesLastModifiedMap = new ConcurrentHashMap<>(128)

    Project project

    File outputDIr

    File resourcesLastModifiedFile

    boolean isCleanCheck

    ExecutorService service

    FileMonitor(Project project, File outputDir) {
        this.project = project
        this.outputDIr = outputDir
        Thread.start {
            init()
        }
    }

    synchronized void init() {
        resourcesLastModifiedFile = new File(outputDIr, "resourcesLastModified.txt")
        if (!resourcesLastModifiedFile.exists()) {
            return
        }
        outputDIr.mkdirs()
        resourcesLastModifiedFile.eachLine("UTF-8") { String line ->
            int firstIndex = line.indexOf(lineSeparator)
            resourcesLastModifiedMap.put(line.substring(0, firstIndex), line)
        }
        isCleanCheck = resourcesLastModifiedMap.isEmpty()

        service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 16)
    }

    synchronized boolean detectModified(Collection<File> files) {

        if (files == null || files.isEmpty()) {
            return
        }

        long start = System.currentTimeMillis()
        LOG.info("FileMonitor: Start to check resources modified ${files.size() > 1 ? files.size() : files.getAt(0)}")
        List<File> resourcesDir = new CopyOnWriteArrayList<>(files)

        List<Future> futures = new ArrayList<>()
        while (!resourcesDir.isEmpty()) {
            final File file = resourcesDir.remove(0)
            futures << service.submit(new Runnable() {
                @Override
                void run() {
                    if (file.isDirectory()) {
                        File[] subFile = file.listFiles()
                        if (subFile == null) {
                            return
                        }
                        resourcesDir.addAll(subFile)
                    } else {
                        detectModified(file)
                    }
                }
            })
        }
        futures.each {
            it.get()
        }
        int duration = System.currentTimeMillis() - start
        LOG.info("FileMonitor: Check resources modified finish, size:${newResourcesLastModifiedMap.size()}, time:${duration}ms")
        return !newResourcesLastModifiedMap.isEmpty()
    }

    synchronized boolean detectModified(File file) {
        String infoStr = resourcesLastModifiedMap.get(file.absolutePath)
        if (isModified(file, infoStr)) {
            if (!isCleanCheck) {
                LOG.info("FileMonitor: Detect file modified ${file}")
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
            if (!isCleanCheck) {
                LOG.info("infoStr empty")
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
            LOG.debug("FileMonitor: infoStr lastModified ${infos[1]},length:${infos[2]}")
            LOG.debug("FileMonitor: file    lastModified ${fileModified}, hashcode:${uniqueId}")
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
        LOG.info("FileMonitor: Update resources modified info")
    }


    int uniqueId(File file) {
        file.getText("UTF-8").hashCode()
    }

    void clearCache() {
        resourcesLastModifiedFile.delete()
    }

    void destroy() {
        service.shutdown
    }
}
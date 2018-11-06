package com.cantalou.gradle.incremental

import org.gradle.api.Project

class ThreadProfile {

    static final String PROPERTIES_FILE_NAME = "local.properties"

    static final String KEY = "fileMonitor.profile"

    static final String SEPARATOR = ";"

    static final int THREAD_INCREMENT = 16

    static final int DEFAULT_THREAD_NUM = Runtime.runtime.availableProcessors() * THREAD_INCREMENT

    static class Info {
        int threadSize = DEFAULT_THREAD_NUM
        int duration
    }

    Project project

    Info info = new Info()

    ThreadProfile(Project project) {
        this.project = project
    }

    Info loadInfo() {

        File propertiesFile = project.rootProject.file(PROPERTIES_FILE_NAME)
        if (!propertiesFile.exists()) {
            return info
        }

        Properties properties = new Properties()
        propertiesFile.withInputStream { is ->
            properties.load(is)
        }

        String profileInfo = properties.getProperty(KEY)
        if (profileInfo == null || profileInfo.isEmpty()) {
            return info
        }

        String[] infos = profileInfo.split(SEPARATOR)
        info.threadSize = infos[0].toInteger()
        info.threadSize = infos[1].toInteger() + THREAD_INCREMENT
    }

    void updateProfile(int duration) {

        if (duration < 100) {
            return
        }

        if (duration < profileDuration) {
            project.println "ThreadProfile: increase thread pool size to ${threadPoolSize}"
            properties.setProperty("fileMonitor.profile", "${duration};${threadPoolSize}")
        } else {
            threadPoolSize = threadPoolSize - Runtime.runtime.availableProcessors()
            if (threadPoolSize < defaultThreadPoolSize) {
                threadPoolSize = defaultThreadPoolSize
            }
            project.println "ThreadProfile: decrease thread pool size to ${threadPoolSize}"
            properties.setProperty("fileMonitor.profile", "${profileDuration};${threadPoolSize}")
        }
        project.rootProject.file("local.properties").withWriter("UTF-8") { out ->
            properties.store(out, "Thread Profile Info")
        }
    }
}
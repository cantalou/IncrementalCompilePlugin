package com.m4399.gradle.incremental.utils

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ThreadProfile {

    private static final Logger LOG = Logging.getLogger(ThreadProfile.class);

    static final String PROPERTIES_FILE_NAME = "local.properties"

    static final String KEY = "fileMonitor.profile"

    static final String SEPARATOR = ";"

    static final int THREAD_INCREMENT = 16

    static final int DEFAULT_THREAD_NUM = Runtime.runtime.availableProcessors() * THREAD_INCREMENT

    static class Info {
        int threadSize = DEFAULT_THREAD_NUM
        int duration

        void setThreadSize(int threadSize) {
            this.threadSize = threadSize
        }

        void setDuration(int duration) {
            this.duration = duration
        }

        int getThreadSize() {
            return threadSize
        }

        int getDuration() {
            return duration
        }
    }

    Project project

    Info info = new Info()

    Properties properties = new Properties()

    ThreadProfile(Project project) {
        this.project = project
    }

    Info loadInfo() {

        File propertiesFile = project.rootProject.file(PROPERTIES_FILE_NAME)
        if (!propertiesFile.exists()) {
            return info
        }

        propertiesFile.withInputStream { is ->
            properties.load(is)
        }

        String profileInfo = properties.getProperty(KEY)
        if (profileInfo == null || profileInfo.isEmpty()) {
            return info
        }

        String[] infos = profileInfo.split(SEPARATOR)
        info.setDuration(infos[0].toInteger())
        info.setThreadSize(infos[1].toInteger() + THREAD_INCREMENT)
        return info
    }

    void updateProfile(int duration) {

        if (duration < 100) {
            return
        }

        if (duration < info.duration) {
            LOG.info("ThreadProfile: increase thread pool size to ${info.threadSize}")
            properties.setProperty("fileMonitor.profile", "${duration};${info.threadSize}")
        } else {
            info.threadSize = info.threadSize - THREAD_INCREMENT
            if (info.threadSize < DEFAULT_THREAD_NUM) {
                info.threadSize = DEFAULT_THREAD_NUM
            }
            LOG.info("ThreadProfile: decrease thread pool size to ${info.threadSize}")
            properties.setProperty("fileMonitor.profile", "${info.duration};${info.threadSize}")
        }
        project.rootProject.file(PROPERTIES_FILE_NAME).withWriter("UTF-8") { out ->
            properties.store(out, "")
        }
    }
}
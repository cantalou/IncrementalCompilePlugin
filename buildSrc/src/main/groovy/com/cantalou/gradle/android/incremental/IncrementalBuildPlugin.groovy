package com.cantalou.gradle.android.incremental

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.cantalou.gradle.android.incremental.extention.IncrementalExtension
import com.cantalou.gradle.android.incremental.tasks.IncrementalJavaCompilerTask
import com.cantalou.gradle.android.incremental.utils.FileMonitor
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.compile.JavaCompile

/**
 * By default, Gradle will disable incremental compile with javac when a modified java source contains constant field,
 * even though the constant value is the same as preview compile, which leads to spending more time in building process.
 *
 * @author LinZhiWei
 * @date 2018年11月01日 10:18
 *
 */
class IncrementalBuildPlugin implements Plugin<Project> {

    private static final Logger LOG = Logging.getLogger(IncrementalBuildPlugin.class)

    Project project

    int deviceSdkVersion = -1

    boolean isApplyBeforeAndroid = true

    @Override
    void apply(Project project) {

        this.project = project

        if (project.hasProperty("android")) {
            isApplyBeforeAndroid = false
        }

        project.getExtensions().add(IncrementalExtension.NAME, IncrementalExtension)

        project.afterEvaluate {
            if (!project.hasProperty("android")) {
                project.println("${project.path}:incrementalBuildPlugin Plugin can only work with Android plugin")
                return
            }

            checkMinSdk()
            enablePreDexLibraries()

            project.android.applicationVariants.all { ApplicationVariantImpl variant ->
                if (!canIncrementalBuild(variant)) {
                    return
                }
                createIncrementalBuildTask(variant)
            }
        }
    }

    void createIncrementalBuildTask(ApplicationVariantImpl variant) {
        LOG.info("${project.path}:incrementalBuildPlugin Start creating incremental build task for ${variant.name}")

        IncrementalJavaCompilerTask task = project.tasks.create("incremental${variant.name.capitalize()}JavaWithJavac", IncrementalJavaCompilerTask)
        task.variant = variant
        task.javaCompiler = variant.javaCompiler
        variant.javaCompiler.dependsOn task

        FileMonitor monitor = new FileMonitor(project, task.getIncrementalOutputs())
        task.monitor = monitor

        def taskGraph = project.gradle.taskGraph
        taskGraph.whenReady {
            if (taskGraph.getAllTasks().any { it.name.startsWith("assemble") }) {
                Thread.start {
                    monitor.detectModified(getSourceFiles(variant), true)
                }
                def safeguardTask = project.tasks.getByName("incremental${variant.name.capitalize()}JavaCompilationSafeguard")
                if (safeguardTask != null) {
                    safeguardTask.enabled = false
                }
            }
        }
    }

    boolean canIncrementalBuild(ApplicationVariantImpl variant) {
        variant.buildType.name == "debug"
    }

    /**
     * Default implementation is to scan all java resource and check if they was modified or not.
     * In feature version we will create background service to add file change monitor to system, then we can just handle the modified file async.
     *
     * @param variant
     * @return
     */
    Collection<File> getSourceFiles(ApplicationVariantImpl variant) {
        JavaCompile javaCompileTask = variant.javaCompiler
        return javaCompileTask.getSource().getFiles()
    }

    void enablePreDexLibraries() {
        IncrementalExtension extension = project.getExtensions().getByName(IncrementalExtension.NAME)
        if (extension != null && !extension.autoPreDex || deviceSdkVersion < 21) {
            return
        }
        if (!isApplyBeforeAndroid) {
            project.println("${project.path}:incrementalBuildPlugin You must apply this plugin before plugin: 'com.android.application' in build.gradle")
        }
        project.android.dexOptions.preDexLibraries = true
        LOG.info("${project.path}:incrementalBuildPlugin enable android.dexOptions.preDexLibraries = true")
    }

    void checkMinSdk() {

        if (deviceSdkVersion > 0) {
            return
        }

        String sdkInfo = "adb shell getprop ro.build.version.sdk".execute().getText().trim()
        if (sdkInfo.matches("\\d+")) {
            deviceSdkVersion = sdkInfo.toInteger()
        }

        if (deviceSdkVersion < 21) {
            return
        }

        project.android.defaultConfig.minSdkVersion = 21
        LOG.info("${project.path}:incrementalBuildPlugin chage android.defaultConfig.minSdkVerion = 21")
    }

}

package com.cantalou.gradle.android.incremental

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.core.DefaultApiVersion
import com.cantalou.gradle.android.incremental.extention.IncrementalExtension
import com.cantalou.gradle.android.incremental.tasks.IncrementalJavaCompilerTask
import com.cantalou.gradle.android.incremental.utils.FileMonitor
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * By default, Gradle will disable incremental compile with javac when a modified java source contains constant field,
 * even though the constant value is the same as preview compile, which leads to spending more time in building process.
 *
 * @author cantalou
 *
 */
class IncrementalBuildPlugin implements Plugin<Project> {

    static boolean loggable = true

    Project project

    int deviceSdkVersion = -1

    boolean isApplyBeforeAndroid = true

    @Override
    void apply(Project project) {

        this.project = project

        if (!canIncrementalBuild()) {
            return
        }

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
                createIncrementalBuildTask(variant)
            }
        }
    }

    void createIncrementalBuildTask(ApplicationVariantImpl variant) {

        if(!variant.buildType.name.equals("debug")){
            return
        }

        if (loggable) {
            project.println("${project.path}:incrementalBuildPlugin Start creating incremental build task for ${variant.name}")
        }

        def taskContainer = project.tasks

        IncrementalJavaCompilerTask task = taskContainer.create("incremental${variant.name.capitalize()}JavaWithJavac", IncrementalJavaCompilerTask)
        task.variant = variant
        task.javaCompiler = variant.javaCompiler
        task.outputs.upToDateWhen { false }
        task.monitor = new FileMonitor(project, task.getIncrementalOutputs())
        variant.javaCompiler.dependsOn task

        def safeguardTask = taskContainer.getByName("incremental${variant.name.capitalize()}JavaCompilationSafeguard")
        if (safeguardTask != null) {
            safeguardTask.enabled = false
        }

        def taskGraph = project.gradle.taskGraph
        taskGraph.whenReady {
            if (taskGraph.getAllTasks().any { it.name.startsWith("assemble") }) {
            }
        }
    }

    boolean canIncrementalBuild() {
        project.gradle.startParameter.taskNames.any {it.matches("assemble(.*?)Debug")}
    }

    void enablePreDexLibraries() {
        IncrementalExtension extension = project.getExtensions().getByName(IncrementalExtension.NAME)
        if (extension != null && extension.disableAutoPreDex || deviceSdkVersion < 21) {
            return
        }
        if (!isApplyBeforeAndroid) {
            project.println("${project.path}:incrementalBuildPlugin You must apply this plugin before plugin: 'com.android.application' in build.gradle")
        }
        project.android.dexOptions.preDexLibraries = true
        if (loggable) {
            project.println("${project.path}:incrementalBuildPlugin enable android.dexOptions.preDexLibraries = true")
        }
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
        if (loggable) {
            project.println("${project.path}:incrementalBuildPlugin chage android.defaultConfig.minSdkVerion = 21")
        }
    }

}

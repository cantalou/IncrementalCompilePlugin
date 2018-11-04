package com.cantalou.gradle.incremental

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.cantalou.gradle.incremental.tasks.FileMonitor
import com.cantalou.gradle.incremental.tasks.PartialJavaCompilerTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

/**
 * By default, Gradle will disable incremental compile with javac when a modified java source contains constant field,
 * even though the constant value is the same as preview compile, which leads to spending more time in building process.
 *
 * @author LinZhiWei
 * @date 2018年11月01日 10:18
 *
 */
class PartialBuildPlugin implements Plugin<Project> {

    Project project

    @Override
    void apply(Project project) {

        this.project = project

        project.afterEvaluate {

            if (!project.hasProperty("android")) {
                project.println("${project.path}:PartialBuildPlugin Plugin must work with Android plugin")
                return
            }

            project.android.applicationVariants.all { ApplicationVariantImpl variant ->
                if (!canPartialBuild(variant)) {
                    return
                }
                createPartialBuildTask(variant)
            }
        }
    }

    void createPartialBuildTask(ApplicationVariantImpl variant) {

        project.println("${project.path}:PartialBuildPlugin Start creating partial build task for ${variant.name}")

        FileMonitor monitor = new FileMonitor(project, "partial-build/${variant.dirName}")

        PartialJavaCompilerTask task = project.tasks.create("partial${variant.name.capitalize()}JavaWithJavac", PartialJavaCompilerTask)
        task.monitor = monitor
        task.variant = variant
        task.javaCompiler = variant.javaCompiler
        variant.javaCompiler.dependsOn task

        Thread.start {
            monitor.detectModified(getSourceFiles(variant))
        }
    }

    boolean canPartialBuild(ApplicationVariantImpl variant) {
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
}

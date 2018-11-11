package com.cantalou.gradle.android.incremental

import com.android.build.gradle.internal.api.ApplicationVariantImpl
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

    @Override
    void apply(Project project) {

        this.project = project

        project.afterEvaluate {
            if (!project.hasProperty("android")) {
                LOG.error("{}:incrementalBuildPlugin Plugin can only work with Android plugin", project.path)
                return
            }

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
        FileMonitor monitor = new FileMonitor(project, "incremental-build/${variant.dirName}")
        IncrementalJavaCompilerTask task = project.tasks.create("incremental${variant.name.capitalize()}JavaWithJavac", IncrementalJavaCompilerTask)
        task.monitor = monitor
        task.variant = variant
        task.javaCompiler = variant.javaCompiler
        variant.javaCompiler.dependsOn task

        def taskGraph = project.gradle.taskGraph
        taskGraph.whenReady {
            if (taskGraph.getAllTasks().any { it.name.startsWith("assemble") }) {
                Thread.start {
                    monitor.detectModified(getSourceFiles(variant), true)
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
}

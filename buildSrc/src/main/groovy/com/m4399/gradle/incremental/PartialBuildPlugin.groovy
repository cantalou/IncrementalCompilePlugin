package com.m4399.gradle.incremental

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.m4399.gradle.incremental.tasks.FileMonitor
import com.m4399.gradle.incremental.tasks.PartialJavaCompilerTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.compile.JavaCompile

/**
 * By default, Gradle will disable incremental compile with javac when a modified java source contains constant field,
 * even though the constant value is the same as preview compile, which leads to spending more time in building process.
 *
 * @author LinZhiWei
 * @date 2018年11月01日 10:18
 *
 * Copyright (c) 2018年, 4399 Network CO.ltd. All Rights Reserved.
 */
class PartialBuildPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.android.applicationVariants.all { ApplicationVariantImpl variant ->
            if (variant.buildType.name == "release") {
                return
            }
            JavaCompile javaCompileTask = variant.javaCompiler
            FileMonitor monitor = new FileMonitor(project,"partial")
            Thread.start {
                monitor.detectModified(javaCompileTask.getSource().getFiles())
            }

            PartialJavaCompilerTask task = project.tasks.create("partialJavaCompilerTask", PartialJavaCompilerTask)
            task.monitor = monitor
        }
    }
}

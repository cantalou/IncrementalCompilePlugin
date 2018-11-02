package com.m4399.gradle.incremental.tasks

import com.google.common.collect.ImmutableList
import com.m4399.gradle.incremental.tasks.FileMonitor
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpecFactory
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.Factory
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.CompilerUtil
import org.gradle.util.DeprecationLogger

/**
 *
 * @date 2018年11月01日 16:09
 *
 * Copyright (c) 2018年, 4399 Network CO.ltd. All Rights Reserved.
 */
class PartialJavaCompilerTask extends JavaCompile {

    FileMonitor monitor

    List<File> changedFiles

    @Override
    protected void compile() {
        //block until detect task finish
        changedFiles = monitor.detectModified()
        DefaultJavaCompileSpec spec = createSpec();
        performCompilation(spec, createCompiler(spec));
    }

    private CleaningJavaCompiler createCompiler(JavaCompileSpec spec) {
        Compiler<JavaCompileSpec> javaCompiler = CompilerUtil.castCompiler(((JavaToolChainInternal) getToolChain()).select(getPlatform()).newCompiler(spec.getClass()))
        return new CleaningJavaCompiler(javaCompiler, getAntBuilderFactory(), getOutputs())
    }

    private void performCompilation(JavaCompileSpec spec, Compiler<JavaCompileSpec> compiler) {
        WorkResult result = compiler.execute(spec);
        setDidWork(result.getDidWork())
    }

    @SuppressWarnings("deprecation")
    private DefaultJavaCompileSpec createSpec() {
        DefaultJavaCompileSpec spec = new DefaultJavaCompileSpecFactory(compileOptions).create();
        spec.setSource(getProject().files(changedFiles.toArray()))
        spec.setDestinationDir(getDestinationDir())
        spec.setWorkingDir(getProject().getProjectDir())
        spec.setTempDir(getTemporaryDir())
        spec.setCompileClasspath(ImmutableList.copyOf(getClasspath()))
        spec.setAnnotationProcessorPath(ImmutableList.copyOf(getEffectiveAnnotationProcessorPath()))
        File dependencyCacheDir = DeprecationLogger.whileDisabled(new Factory<File>() {
            @Override
            @SuppressWarnings("deprecation")
            public File create() {
                return getDependencyCacheDir()
            }
        });
        spec.setDependencyCacheDir(dependencyCacheDir)
        spec.setTargetCompatibility(getTargetCompatibility())
        spec.setSourceCompatibility(getSourceCompatibility())
        spec.setCompileOptions(compileOptions);
        return spec
    }
}

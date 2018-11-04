package com.cantalou.gradle.incremental.tasks

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.AndroidProject
import com.google.common.collect.ImmutableList
import org.gradle.api.DefaultTask
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpecFactory
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.CompilerUtil

import com.cantalou.gradle.incremental.tasks.FileMonitor

/**
 *
 * @date 2018年11月01日 16:09
 *
 */
class PartialJavaCompilerTask extends DefaultTask {

    FileMonitor monitor

    List<File> changedFiles = new ArrayList<>()

    ApplicationVariantImpl variant

    JavaCompile javaCompiler

    @InputFiles
    @SkipWhenEmpty
    List<File> getChangeFiles() {
        return monitor.getModifiedFile()
    }

    @InputDirectory
    @SkipWhenEmpty
    File getGenerateDir() {
        return new File(project.buildDir, "${AndroidProject.FD_GENERATED}/source")
    }

    @OutputDirectory
    File getCompileOutputs() {
        return javaCompiler.destinationDir
    }

    @TaskAction
    protected void compile() {

        File[] destDir = javaCompiler.destinationDir.listFiles()
        if (destDir == null || destDir.length == 0) {
            project.println("${project.path}:PartialJavaCompilerTask ouput dir is null , need full recompile")
            return
        }
        monitor.detectModified([getGenerateDir()])
        changedFiles = monitor.getModifiedFile()

        //block until detect task finish
        if (changedFiles.size() > 40) {
            project.println("${project.path}:PartialJavaCompilerTask Detect modified file lager than 40, use normal java compiler")
            javaCompiler.doLast {
                if (javaCompiler.state.didWork) {
                    monitor.updateResourcesModified()
                } else {
                    monitor.clearCache()
                }
            }
            return
        }

        javaCompiler.enabled = false
        project.println("${project.path}:PartialJavaCompilerTask change ${javaCompiler}.enable=false")

        if (changedFiles == null || changedFiles.isEmpty()) {
            project.println("${project.path}:PartialJavaCompilerTask UP-TO-DATE")
            return
        }

        DefaultJavaCompileSpec spec = createSpec();
        performCompilation(spec, createCompiler(spec));
    }

    private Compiler<JavaCompileSpec> createCompiler(JavaCompileSpec spec) {
        return CompilerUtil.castCompiler(((JavaToolChainInternal) javaCompiler.getToolChain()).select(javaCompiler.getPlatform()).newCompiler(spec.getClass()))
    }

    private void performCompilation(JavaCompileSpec spec, Compiler<JavaCompileSpec> compiler) {
        WorkResult result
        try {
            result = compiler.execute(spec);
        } catch (Throwable t) {
            monitor.clearCache()
            throw t
        }
        setDidWork(result.getDidWork())
        if (!result.getDidWork()) {
            monitor.clearCache()
        } else {
            monitor.updateResourcesModified()
        }
    }

    @SuppressWarnings("deprecation")
    private DefaultJavaCompileSpec createSpec() {
        DefaultJavaCompileSpec spec = new DefaultJavaCompileSpecFactory(javaCompiler.getOptions()).create();
        spec.setSource(getProject().files(changedFiles.toArray()))
        spec.setDestinationDir(javaCompiler.getDestinationDir())
        spec.setWorkingDir(getProject().getProjectDir())
        spec.setTempDir(javaCompiler.getTemporaryDir())
        List<File> classpath = javaCompiler.getClasspath().asList()
        classpath << javaCompiler.destinationDir
        spec.setCompileClasspath(classpath)
        spec.setAnnotationProcessorPath(ImmutableList.copyOf(javaCompiler.getEffectiveAnnotationProcessorPath()))
        spec.setTargetCompatibility(javaCompiler.getTargetCompatibility())
        spec.setSourceCompatibility(javaCompiler.getSourceCompatibility())
        spec.setCompileOptions(javaCompiler.getOptions());
        return spec
    }
}

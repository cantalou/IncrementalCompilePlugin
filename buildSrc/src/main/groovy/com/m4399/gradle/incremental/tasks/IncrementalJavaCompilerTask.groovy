package com.m4399.gradle.incremental.tasks

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.AndroidProject
import com.m4399.gradle.incremental.analysis.impl.ClasspathAnalysis
import com.m4399.gradle.incremental.analysis.impl.DirectoryAnalysis
import com.m4399.gradle.incremental.utils.FileMonitor
import com.google.common.collect.ImmutableList
import org.gradle.api.DefaultTask
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpecFactory
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.CompilerUtil

/**
 *
 * @date 2018年11月01日 16:09
 *
 */
class IncrementalJavaCompilerTask extends DefaultTask {

    private static final Logger LOG = Logging.getLogger(IncrementalJavaCompilerTask.class)

    List<String> changedFiles = new ArrayList<>()

    ApplicationVariantImpl variant

    JavaCompile javaCompiler

    FileMonitor monitor

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
    File getIncrementalOutputs() {
        return new File(project.buildDir, "${AndroidProject.FD_INTERMEDIATES}/incremental/${variant.dirName}")
    }

    @OutputDirectory
    File getCompileClassesOutputs() {
        return new File(getIncrementalOutputs(), "classes")
    }

    @OutputDirectory
    File getCompileClasspathOutputs() {
        return new File(getIncrementalOutputs(), "libs")
    }

    @TaskAction
    protected void compile(IncrementalTaskInputs inputs) {

        long start = System.currentTimeMillis()

        fullCompileCallback()

        LOG.lifecycle("${project.path}:${getName()}: Start to check java resources modified")
        changedFiles = detectSourceFiles()

        LOG.lifecycle("${project.path}:${getName()}: Start to check classpath resources modified")
        def changedJarFiles = detectClasspathFiles()

        File[] destDir = javaCompiler.destinationDir.listFiles()
        if (destDir == null || destDir.length == 0) {
            LOG.lifecycle("${project.path}:${getName()} class ouput dir is null , need full recompile")
            return
        }

        if (changedFiles.size() > 40) {
            LOG.lifecycle("${project.path}:${getName()} Detect modified file lager than 40, use normal java compiler")
            return
        }

        ClasspathAnalysis ca = new ClasspathAnalysis(getCompileClasspathOutputs().listFiles() as List, javaCompiler.classpath.getFiles())
        if(ca.isFullRebuildNeeded()){
            LOG.lifecycle("${project.path}:${getName()} ${ca.fullRebuildCause} , need full recompile")
            return
        }

        if (changedFiles == null || changedFiles.isEmpty()) {
            LOG.lifecycle("${project.path}:${getName()} UP-TO-DATE")
            javaCompiler.enabled = false
            LOG.lifecycle("${project.path}:${getName()} change ${javaCompiler}.enable=false")
            return
        }

        LOG.lifecycle("${project.path}:${getName()} file need to be compile: ")
        changedFiles.each {
            LOG.lifecycle(it.toString())
        }

        def incrementalClassesOutputs = getCompileClassesOutputs()
        incrementalClassesOutputs.deleteDir()
        incrementalClassesOutputs.mkdirs()

        try {
            DefaultJavaCompileSpec spec = createSpec()
            performCompilation(spec, createCompiler(spec))
        } catch (Throwable e) {
            LOG.lifecycle("${project.path}:${getName()} incremental compile error ", e)
            return
        }

        if (!getDidWork()) {
            LOG.lifecycle("${project.path}:${getName()} getDidWork return false, need full compile")
            return
        }

        try {
            def preClasspath = javaCompiler.classpath.getFiles() as List
            preClasspath.addAll(variant.variantData.scope.globalScope.androidBuilder.getBootClasspath(false))

            DirectoryAnalysis da = new DirectoryAnalysis(javaCompiler.destinationDir, incrementalClassesOutputs, preClasspath)
            if (da.isFullRebuildNeeded()) {
                LOG.lifecycle("${project.path}:${getName()} ${da.getFullRebuildCause()}, need full recompile")
                return
            }

            WorkResult result = project.copy {
                from incrementalClassesOutputs
                into javaCompiler.destinationDir
            }

            if (!result.didWork) {
                LOG.lifecycle("${project.path}:${getName()} failed to copy compiled class from ${incrementalClassesOutputs} to ${javaCompiler.destinationDir}")
                return
            }

            copyClasspathJar(changedJarFiles)
            monitor.updateResourcesModified()
            javaCompiler.enabled = false
            LOG.lifecycle("${project.path}:${getName()} change ${javaCompiler}.enable=false")
            LOG.lifecycle("${project.path}:${getName()} completed. Took ${(System.currentTimeMillis() - start)/1000.0} secs")
        } catch (Throwable throwable) {
            LOG.lifecycle("${project.path}:${getName()} error", throwable)
        }
    }

    void fullCompileCallback() {
        javaCompiler.doLast {
            if (javaCompiler.state.didWork) {
                monitor.updateResourcesModified()
                copyClasspathJar(javaCompiler.classpath.getFiles())
            } else {
                monitor.clearCache()
            }
        }
    }

    void copyClasspathJar(def jarFiles) {
        jarFiles.each { File jarFile ->
            if (jarFile.isDirectory() || !jarFile.name.endsWith(".jar")) {
                return
            }
            project.copy {
                from jarFile
                into getCompileClasspathOutputs()
                rename jarFile.name, "${Math.abs(jarFile.hashCode())}-${jarFile.name}"
            }
        }
    }

    Compiler<JavaCompileSpec> createCompiler(JavaCompileSpec spec) {
        return CompilerUtil.castCompiler(((JavaToolChainInternal) javaCompiler.getToolChain()).select(javaCompiler.getPlatform()).newCompiler(spec.getClass()))
    }

    void performCompilation(JavaCompileSpec spec, Compiler<JavaCompileSpec> compiler) {
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
        }
    }

    @SuppressWarnings("deprecation")
    DefaultJavaCompileSpec createSpec() {
        DefaultJavaCompileSpec spec = new DefaultJavaCompileSpecFactory(javaCompiler.getOptions()).create();
        spec.setSource(getProject().files(changedFiles.toArray()))
        spec.setDestinationDir(getCompileClassesOutputs())
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

    /**
     * Default implementation is to scan all java resource and check if they was modified.
     * In the feature version we will create background service to add file change monitor to os system, then we can just handle the modified file async.
     *
     * @param variant
     * @return
     */
    List<File> detectSourceFiles() {
        Collection<File> sourceFiles = javaCompiler.getSource().getFiles()
        return monitor.detectModified(sourceFiles)
    }


    List<File> detectClasspathFiles() {
        Collection<File> classpathFiles = javaCompiler.classpath.getFiles()
        return monitor.detectModified(classpathFiles)
    }

}
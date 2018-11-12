package com.cantalou.gradle.android.incremental.tasks

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.AndroidProject
import com.cantalou.gradle.android.incremental.utils.Ref
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

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.regex.Pattern

/**
 *
 * @date 2018年11月01日 16:09
 *
 */
class IncrementalJavaCompilerTask extends DefaultTask {

    private static final Logger LOG = Logging.getLogger(IncrementalJavaCompilerTask.class)

    com.cantalou.gradle.android.incremental.utils.FileMonitor monitor

    List<String> changedFiles = new ArrayList<>()

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
        return new File(project.buildDir, "${AndroidProject.FD_INTERMEDIATES}/incremental/${variant.dirName}")
    }

    @TaskAction
    protected void compile(IncrementalTaskInputs inputs) {

        monitor.detectModified([getGenerateDir()], false)

        File[] destDir = javaCompiler.destinationDir.listFiles()
        if (destDir == null || destDir.length == 0) {
            LOG.lifecycle("${project.path}:${getName()} ouput dir is null , need full recompile")
            fullCompileCallback()
            return
        }
        monitor.detectModified([getGenerateDir()], false)
        changedFiles = monitor.getModifiedFile()
        LOG.lifecycle("${project.path}:${getName()} file need to be recompile: ")
        changedFiles.each {
            LOG.lifecycle(it.toString())
        }

        //block until detect task finish
        if (changedFiles.size() > 40) {
            LOG.lifecycle("${project.path}:${getName()} Detect modified file lager than 40, use normal java compiler")
            fullCompileCallback()
            return
        }

        if (changedFiles == null || changedFiles.isEmpty()) {
            LOG.lifecycle("${project.path}:${getName()} UP-TO-DATE")
            javaCompiler.enabled = false
            LOG.lifecycle("${project.path}:${getName()} change ${javaCompiler}.enable=false")
            return
        }

        def sourceDirPaths = []
        Ref.getValue(javaCompiler, SourceTask.class, "source").each {
            sourceDirPaths << it.getDir().absolutePath
        }

        def jars = javaCompiler.classpath.getFiles().collect { it.toURL() }
        jars.addAll(variant.variantData.scope.globalScope.androidBuilder.getBootClasspath(false).collect { it.toURL() })
        jars << javaCompiler.destinationDir.toURL()

        def classpath = jars.toArray(new URL[0])
        def preCompileClasses = []
        URLClassLoader preClassloader = new URLClassLoader(classpath)
        for (int i = 0; i < changedFiles.size(); i++) {
            String modifiedFile = changedFiles.get(i)
            for (int j = 0; j < sourceDirPaths.size(); j++) {
                String sourcePath = sourceDirPaths.get(j)
                String className = convertClassName(sourcePath, modifiedFile)
                if (className != null) {
                    try {
                        preCompileClasses << preClassloader.loadClass(className)
                    } catch (ClassNotFoundException e) {
                    }
                    break
                }
            }
        }


        try {
            DefaultJavaCompileSpec spec = createSpec()
            performCompilation(spec, createCompiler(spec))
        } catch (Throwable e) {
            LOG.lifecycle("${project.path}:${getName()} incremental compile error ", e)
            return
        }

        if(!getDidWork()){
            LOG.lifecycle("${project.path}:${getName()} getDidWork return false, need full compile")
            return
        }

        URLClassLoader incrementalClassloader = new URLClassLoader(classpath)
        for (Class preCompileClazz : preCompileClasses) {
            Class incrementalCompileClazz = incrementalClassloader.loadClass(preCompileClazz.name)
            if (checkFullCompile(preCompileClazz, incrementalCompileClazz)) {
                LOG.lifecycle("${project.path}:${getName()} checkFullCompile ${preCompileClazz.name} need full compile")
                return
            }
        }

        javaCompiler.enabled = false
        LOG.lifecycle("${project.path}:${getName()} change ${javaCompiler}.enable=false")
    }

    void fullCompileCallback() {
        javaCompiler.doLast {
            if (javaCompiler.state.didWork) {
                monitor.updateResourcesModified()
            } else {
                monitor.clearCache()
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
        } else {
            monitor.updateResourcesModified()
        }
    }

    @SuppressWarnings("deprecation")
    DefaultJavaCompileSpec createSpec() {
        DefaultJavaCompileSpec spec = new DefaultJavaCompileSpecFactory(javaCompiler.getOptions()).create();
        spec.setSource(getProject().files(changedFiles.toArray()))
        spec.setDestinationDir(javaCompiler.destinationDir)
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

    String convertClassName(String sourcePath, String modifiedFile) {
        String className = modifiedFile.replace(sourcePath, "")
        if (className.length() == modifiedFile.length()) {
            return null
        }

        if (className.startsWith(File.separator)) {
            className = className.substring(1)
        }

        String[] parts = className.split(Pattern.quote(File.separator))
        parts[parts.length - 1] = new File(modifiedFile).name.replace(".java", "")
        return parts.join(".")
    }

    boolean checkFullCompile(Class preCompileClazz, Class incrementalCompileClazz) {
        int modifierFlag = Modifier.STATIC | Modifier.FINAL
        def preCompileFields = preCompileClazz.getDeclaredFields()
        for (Field preField : preCompileFields) {
            preField.setAccessible(true)
            if ((preField.modifiers & modifierFlag) != modifierFlag) {
                continue
            }
            Class type = preField.getType()
            if (!(type.isPrimitive() || type == String.class)) {
                continue
            }
            try {
                Field incrementalField = incrementalCompileClazz.getDeclaredField(preField.name)
                incrementalField.setAccessible(true)
                if (!preField.get(null).equals(incrementalField.get(null))) {
                    LOG.lifecycle("${project.path}:${getName()} field '${preField.name}' was modified from ${preField.get(null)} to ${incrementalField.get(null)}")
                    return true
                }
            } catch (NoSuchFieldException e) {
                LOG.lifecycle("${project.path}:${getName()} field '${preField.name}' was missing from ${incrementalCompileClazz.name}")
                return true
            }
        }

        return false
    }
}

















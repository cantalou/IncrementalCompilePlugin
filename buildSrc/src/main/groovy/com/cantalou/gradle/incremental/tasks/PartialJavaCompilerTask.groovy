package com.cantalou.gradle.incremental.tasks

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.AndroidProject
import com.cantalou.gradle.incremental.tasks.FileMonitor
import com.google.common.collect.ImmutableList
import com.intellij.psi.templateLanguages.OuterLanguageElement
import groovy.json.internal.FastStringUtils
import org.apache.tools.ant.util.StringUtils
import org.codehaus.groovy.util.StringUtil
import org.gradle.api.DefaultTask
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpecFactory
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.CompilerUtil

import java.awt.Label
import java.lang.reflect.Field
import java.lang.reflect.Modifier

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
        return new File(project.buildDir, "${AndroidProject.FD_GENERATED}/partial")
    }

    @TaskAction
    protected void compile(IncrementalTaskInputs inputs) {

        File[] destDir = javaCompiler.destinationDir.listFiles()
        if (destDir == null || destDir.length == 0) {
            project.println("${project.path}:partialJavaCompilerTask ouput dir is null , need full recompile")
            javaCompiler.doLast {
                if (javaCompiler.state.didWork) {
                    monitor.updateResourcesModified()
                } else {
                    monitor.clearCache()
                }
            }
            return
        }
        monitor.detectModified([getGenerateDir()], false)
        changedFiles = monitor.getModifiedFile()

        //block until detect task finish
        if (changedFiles.size() > 40) {
            project.println("${project.path}:partialJavaCompilerTask Detect modified file lager than 40, use normal java compiler")
            javaCompiler.doLast {
                if (javaCompiler.state.didWork) {
                    monitor.updateResourcesModified()
                } else {
                    monitor.clearCache()
                }
            }
            return
        }

        if (changedFiles == null || changedFiles.isEmpty()) {
            project.println("${project.path}:partialJavaCompilerTask UP-TO-DATE")
            javaCompiler.enabled = false
            project.println("${project.path}:partialJavaCompilerTask change ${javaCompiler}.enable=false")
            return
        }

        def sourcePaths = []
        Ref.getValue(javaCompiler, SourceTask.class, "source").each {
            sourcePaths << it.getDir().absolutePath
        }

        int modifierFlag = Modifier.STATIC | Modifier.FINAL
        boolean fullCompile = false

        URLClassLoader classLoader = new URLClassLoader(new URL[1] { new URL(javaCompiler.destinationDir) })
        for (int i = 0; i < changedFiles.size() && !fullCompile; i++) {
            File modifiedFile = changedFiles.get(i)
            for (int j = 0; i < sourcePaths.size() && !fullCompile; j++) {
                String sourcePath = sourcePaths.get(j)
                String className = convertClassName(sourcePath, modifiedFile)
                if (className == null) {
                    continue
                }
                fullCompile = fullCompile(classLoader.loadClass(className), modifiedFile)
            }
        }

        javaCompiler.enabled = false
        project.println("${project.path}:partialJavaCompilerTask change ${javaCompiler}.enable=false")
        DefaultJavaCompileSpec spec = createSpec();
        performCompilation(spec, createCompiler(spec));
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

    String convertClassName(String sourcePath, File modifiedFile) {
        String className = modifiedFile.absolutePath.replace(sourcePath)
        if (className.length() == modifiedFile.absolutePath.length()) {
            return null
        }
        String[] parts = className.split(File.separator)
        parts[parts.length - 1] = modifiedFile.name
        return parts.join(".")
    }

    boolean fullCompile(Class clazz, File sourceFile) {
        return clazz.getDeclaredFields().any { it.modifiers & modifierFlag == modifierFlag }
    }
}

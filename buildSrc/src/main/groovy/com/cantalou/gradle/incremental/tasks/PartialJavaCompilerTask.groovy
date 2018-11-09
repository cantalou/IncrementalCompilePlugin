package com.cantalou.gradle.incremental.tasks

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.AndroidProject
import com.cantalou.gradle.incremental.utils.FileMonitor
import com.cantalou.gradle.incremental.utils.JarMerger
import com.cantalou.gradle.incremental.utils.Ref
import com.google.common.collect.ImmutableList
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

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.regex.Pattern

/**
 *
 * @date 2018年11月01日 16:09
 *
 */
class PartialJavaCompilerTask extends DefaultTask {

    FileMonitor monitor

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
        return new File(project.buildDir, "${AndroidProject.FD_INTERMEDIATES}/partial/${variant.dirName}")
    }

    @OutputFile
    File getCombineJar() {
        return new File(project.buildDir, "${AndroidProject.FD_INTERMEDIATES}/partial/${variant.dirName}/combine.jar")
    }

    @TaskAction
    protected void compile(IncrementalTaskInputs inputs) {

        monitor.detectModified([getGenerateDir()], false)

        File[] destDir = javaCompiler.destinationDir.listFiles()
        if (destDir == null || destDir.length == 0) {
            project.println("${project.path}:${getName()} ouput dir is null , need full recompile")
            fullCompileCallback()
            return
        }

        if(!getCombineJar().exists()){
            project.println("${project.path}:${getName()} ouput combind.jar was miss , need full recompile")
            fullCompileCallback()
            return
        }

        changedFiles = monitor.getModifiedFile()
        project.println("${project.path}:${getName()} file need to be recompile: ")
        changedFiles.each {
            project.println it
        }

        //block until detect task finish
        if (changedFiles.size() > 40) {
            project.println("${project.path}:${getName()} Detect modified file lager than 40, use normal java compiler")
            fullCompileCallback()
            return
        }

        if (changedFiles == null || changedFiles.isEmpty()) {
            project.println("${project.path}:${getName()} UP-TO-DATE")
            javaCompiler.enabled = false
            project.println("${project.path}:${getName()} change ${javaCompiler}.enable=false")
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
        changedFiles.each { String modifiedFile ->
            for (int j = 0; j < sourceDirPaths.size(); j++) {
                String sourcePath = sourceDirPaths.get(j)
                String className = convertClassName(sourcePath, modifiedFile)
                if (className != null) {
                    preCompileClasses << preClassloader.loadClass(className)
                    break
                }
            }
        }

        DefaultJavaCompileSpec spec = createSpec()
        performCompilation(spec, createCompiler(spec))

        URLClassLoader partialClassloader = new URLClassLoader(classpath)
        for (Class preCompileClazz : preCompileClasses) {
            Class partialCompileClazz = partialClassloader.loadClass(preCompileClazz.name)
            if (checkFullCompile(preCompileClazz, partialCompileClazz)) {
                project.println("${project.path}:${getName()} checkFullCompile ${preCompileClazz.name} need full compile")
                return
            }
        }

        javaCompiler.enabled = false
        project.println("${project.path}:${getName()} change ${javaCompiler}.enable=false")
        createProjectCompileJar(getName())
    }

    void fullCompileCallback() {
        javaCompiler.doLast {
            if (javaCompiler.state.didWork) {
                monitor.updateResourcesModified()
                createProjectCompileJar(javaCompiler.name)
            } else {
                // monitor.clearCache()
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
        classpath << getCombineJar()
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

    boolean checkFullCompile(Class preCompileClazz, Class partialCompileClazz) {
        int modifierFlag = Modifier.STATIC | Modifier.FINAL
        def preCompileFields = preCompileClazz.getDeclaredFields()
        for (Field preField : preCompileFields) {
            preField.setAccessible(true)
            if ((preField.modifiers & modifierFlag) != modifierFlag) {
                continue
            }
            def type = preField.getType()
            if (!(type.isPrimitive() || type.equals(String.class))) {
                continue
            }
            try {
                Field partialField = partialCompileClazz.getDeclaredField(preField.name)
                partialField.setAccessible(true)
                if (!preField.get(null).equals(partialField.get(null))) {
                    project.println("${project.path}:${getName()} field '${preField.name}' was modified from ${preField.get(null)} to ${partialField.get(null)}")
                    return true
                }
            } catch (NoSuchFieldException e) {
                project.println("${project.path}:${getName()} field '${preField.name}' was missing from ${partialCompileClazz.name}")
                return true
            }
        }

        return false
    }

    void createProjectCompileJar(String name) {
        File destJar = getCombineJar()
        destJar.getParentFile().mkdirs()
        JarMerger merger = new JarMerger(destJar)
        merger.addFolder(javaCompiler.destinationDir)
        merger.close()
        project.println("${project.path}:${name} crate ${destJar}")
    }
}

















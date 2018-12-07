package com.m4399.gradle.incremental.analysis.impl;

import com.m4399.gradle.incremental.analysis.AbstractAnalysis;

import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import groovy.lang.Closure;

/**
 * Analyse currentCompile class and preCompile class
 */
public class DirectoryAnalysis extends AbstractAnalysis<File> {

    private static final Logger LOG = Logging.getLogger(DirectoryAnalysis.class);

    protected List<File> jarFiles;

    protected File copyPreCompile;

    public DirectoryAnalysis(File preCompileResource, File currentCompileResource, List<File> jarFiles) throws Exception {
        super(preCompileResource, currentCompileResource);
        this.jarFiles = jarFiles;
        copyPreCompile = new File(currentCompileResource.getParent(), "preClasses");
        FileUtils.deleteDirectory(copyPreCompile);
    }

    @Override
    public void analysis() throws Exception {

        // contains all the class since last compile
        final List<Class> preCompiledClass = new ArrayList<>();
        final URLClassLoader preClassloader = new URLClassLoader(file2URL(jarFiles, preCompileResource));

        //incremental compile classpath info, this classloader only contains common classpath jars and the class that compiled from modified java file
        final int classNameStartIndex = currentCompileResource.getAbsolutePath()
                                                              .length() + 1;
        ResourceGroovyMethods.eachFileRecurse(currentCompileResource, new Closure<File>(this) {
            @Override
            public File call(Object arguments) {
                File classFile = (File) arguments;
                String sourcePath = classFile.getAbsolutePath();
                if (!sourcePath.endsWith(".class")) {
                    return null;
                }
                // D:/project/build/classes/com/package/className.class  -> com.package.className
                String className = sourcePath.substring(classNameStartIndex, sourcePath.lastIndexOf("."))
                                             .replace(File.separatorChar, (char) '.');
                try {
                    preCompiledClass.add(preClassloader.loadClass(className));
                } catch (ClassNotFoundException e) {
                    LOG.lifecycle("new class {} can not be found in pre compile ignore", className);
                }

                String fullClassName = sourcePath.substring(classNameStartIndex);
                try {
                    FileUtils.copyFile(new File(preCompileResource, fullClassName), new File(copyPreCompile, fullClassName));
                } catch (IOException e) {
                    throw new IllegalStateException("copy file failure", e);
                }

                return null;
            }
        });

        FileUtils.copyDirectory(currentCompileResource, preCompileResource);

        final URLClassLoader currentClassloader = new URLClassLoader(file2URL(jarFiles, preCompileResource));
        for (Class preClazz : preCompiledClass) {
            try {
                Class currentCompileClazz = currentClassloader.loadClass(preClazz.getName());
                ClassAnalysis classAnalysis = new ClassAnalysis(preClazz, currentCompileClazz);
                if (classAnalysis.isFullRebuildNeeded()) {
                    setFullRebuildCause(classAnalysis.getFullRebuildCause());
                    //restore the class file
                    FileUtils.copyDirectory(copyPreCompile, preCompileResource);
                    break;
                }
            } catch (ClassNotFoundException e) {
                LOG.debug("class {} was removed", preClazz);
            }
        }
    }


    /**
     * Convert file to URL object for creating Classloader
     *
     * @param jarFiles
     * @param classDir
     * @return
     * @throws MalformedURLException
     */
    private URL[] file2URL(List<File> jarFiles, File classDir) throws MalformedURLException {
        List<File> compileClasspath = new ArrayList<>(jarFiles);
        compileClasspath.add(classDir);
        URL[] urls = new URL[compileClasspath.size()];
        for (int i = 0; i < compileClasspath.size(); i++) {
            urls[i] = compileClasspath.get(i)
                                      .toURL();
        }
        return urls;
    }
}

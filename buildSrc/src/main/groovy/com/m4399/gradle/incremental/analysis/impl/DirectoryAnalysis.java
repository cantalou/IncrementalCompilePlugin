package com.m4399.gradle.incremental.analysis.impl;

import com.m4399.gradle.incremental.analysis.AbstractAnalysis;

import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
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

    public DirectoryAnalysis(File preCompileResource, File currentCompileResource, List<File> jarFiles) throws Exception {
        super(preCompileResource, currentCompileResource);
        this.jarFiles = jarFiles;
    }

    @Override
    public void analysis() throws Exception {

        //incremental compile classpath info, this classloader only contains common classpath jars and the class that compiled from modified java file
        final URLClassLoader currentClassloader = new URLClassLoader(file2URL(jarFiles, currentCompileResource));
        final List<Class> currentClasses = new ArrayList<>();
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
                // D:/project/build/classes/com/package/className.class -> com.package.className
                String className = sourcePath.substring(classNameStartIndex, sourcePath.lastIndexOf("."))
                                             .replace(File.separatorChar, (char) '.');
                try {
                    currentClasses.add(currentClassloader.loadClass(className));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
                return null;
            }
        });

        final URLClassLoader preClassloader = new URLClassLoader(file2URL(jarFiles, preCompileResource));
        for (Class currentClazz : currentClasses) {
            try {
                Class preCompileClazz = preClassloader.loadClass(currentClazz.getName());
                ClassAnalysis classAnalysis = new ClassAnalysis(preCompileClazz, currentClazz);
                if (classAnalysis.isFullRebuildNeeded()) {
                    setFullRebuildCause(classAnalysis.getFullRebuildCause());
                    break;
                }
            } catch (ClassNotFoundException e) {
                LOG.debug("class {} was removed", currentClazz);
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

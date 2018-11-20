package com.cantalou.gradle.android.incremental.analysis.impl;

import com.cantalou.gradle.android.incremental.analysis.AbstractAnalysis;

import org.codehaus.groovy.runtime.ResourceGroovyMethods;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import groovy.lang.Closure;

/**
 * @author cantalou
 * @date 2018-11-18 11:39
 */
public class DirectoryAnalysis extends AbstractAnalysis<File> {

    protected ClassLoader parentClassloader;

    public DirectoryAnalysis(File preCompileResource, File currentCompileResource, List<File> jarFiles) throws Exception {
        super(preCompileResource, currentCompileResource);
        URL[] urls = new URL[jarFiles.size()];
        for (int i = 0; i < jarFiles.size(); i++) {
            urls[i] = jarFiles.get(i)
                              .toURL();
        }
        parentClassloader = new URLClassLoader(urls);
    }

    @Override
    public void analysis() throws Exception {

        final URLClassLoader currentClassloader = new URLClassLoader(new URL[]{currentCompileResource.toURL()}, parentClassloader) {
            @Override
            public Class<?> loadClass(String s) throws ClassNotFoundException {
                Class clazz = null;
                try {
                    clazz = this.findClass(s);
                } catch (ClassNotFoundException var10) {
                }
                return clazz != null ? clazz : super.loadClass(s);
            }
        };

        final List<Class> currentClasses = new ArrayList<>();
        final int classNameStartIndex = currentCompileResource.getAbsolutePath()
                                                              .length() + 1;
        ResourceGroovyMethods.eachFileRecurse(currentCompileResource, new Closure<File>(this) {
            @Override
            public File call(Object arguments) {
                File classFile = (File) arguments;
                if (!classFile.getName()
                              .endsWith(".class")) {
                    return null;
                }
                String sourcePath = classFile.getAbsolutePath();
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

        final URLClassLoader preClassloader = new URLClassLoader(new URL[]{preCompileResource.toURL()}, parentClassloader);
        for (Class currentClazz : currentClasses) {
            try {
                Class preCompileClazz = preClassloader.loadClass(currentClazz.getName());
                ClassAnalysis classAnalysis = new ClassAnalysis(preCompileClazz, currentClazz);
                if (classAnalysis.isFullRebuildNeeded()) {
                    setFullRebuildCause(classAnalysis.getFullRebuildCause());
                    break;
                }
            } catch (ClassNotFoundException e) {
            }
        }
    }
}

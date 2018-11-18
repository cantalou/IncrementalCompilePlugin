package com.cantalou.gradle.android.incremental.analysis;

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

    public DirectoryAnalysis(File preCompileResource, File currentCompileResource, List<File> jarFiles) throws MalformedURLException {
        super(preCompileResource, currentCompileResource);
        parentClassloader = new URLClassLoader(toUrlArray(jarFiles));
    }

    private static URL[] toUrlArray(List<File> jarFiles) throws MalformedURLException {
        URL[] urls = new URL[jarFiles.size()];
        for (int i = 0; i < jarFiles.size(); i++) {
            urls[i] = jarFiles.get(i)
                              .toURL();
        }
        return urls;
    }

    @Override
    public void analysis() throws Exception {

        final URLClassLoader currentClassloader = new URLClassLoader(new URL[]{getCurrentCompileResource().toURL()}, parentClassloader) {
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
        final String classDirPath = getCurrentCompileResource().getAbsolutePath();
        ResourceGroovyMethods.eachFileRecurse(getCurrentCompileResource(), new Closure<File>(this) {
            @Override
            public File call(Object arguments) {
                File classFile = (File) arguments;
                if (!classFile.getName()
                              .endsWith(".class")) {
                    return null;
                }
                String sourcePath = classFile.getAbsolutePath();
                String className = sourcePath.substring(classDirPath.length() + 1, sourcePath.lastIndexOf("."))
                                             .replace(File.separatorChar, (char) '.');
                try {
                    currentClasses.add(currentClassloader.loadClass(className));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }

                return super.call(arguments);
            }
        });

        final URLClassLoader preClassloader = new URLClassLoader(new URL[]{getCurrentCompileResource().toURL()}, parentClassloader);

        for (Class currentCompileClazz : currentClasses) {
            try {
                Class preCompileClazz = preClassloader.loadClass(currentCompileClazz.getName());
                ClassAnalysis classAnalysis = new ClassAnalysis(preCompileClazz, currentCompileClazz);
                classAnalysis.analysis();
                if (classAnalysis.isFullRebuildNeeded()) {
                    LOG.lifecycle(classAnalysis.getFullRebuildCause());
                    setFullRebuildCause(classAnalysis.getFullRebuildCause());
                    break;
                }
            } catch (ClassNotFoundException e) {
            }
        }
    }
}

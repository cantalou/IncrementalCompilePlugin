package com.m4399.gradle.incremental.analysis.impl;

import com.m4399.gradle.incremental.analysis.AbstractAnalysis;

import java.io.File;
import java.util.Collection;

public class ClasspathAnalysis extends AbstractAnalysis<Collection<File>> {

    public ClasspathAnalysis(Collection<File> preCompileResource, Collection<File> currentCompileResource) throws Exception {
        super(preCompileResource, currentCompileResource);
    }

    @Override
    public void analysis() throws Exception {

        if (preCompileResource == null || preCompileResource.isEmpty()) {
            setFullRebuildCause("classpath output is null or empty");
            return;
        }

        if (!currentCompileResource.isEmpty()) {
            return;
        }

        for (File jarFile : currentCompileResource) {
            JarAnalysis ja = new JarAnalysis(preCompileResource, currentCompileResource, jarFile);
            if (ja.isFullRebuildNeeded()) {
                setFullRebuildCause(ja.getFullRebuildCause());
                return;
            }
        }
    }
}

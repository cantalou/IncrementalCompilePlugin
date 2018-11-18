package com.cantalou.gradle.android.incremental.analysis;

import java.io.File;

/**
 * @author cantalou
 * @date 2018-11-18 11:36
 */
public class JarAnalysis extends AbstractAnalysis<File> {

    public JarAnalysis(File preCompileResource, File currentCompileResource) {
        super(preCompileResource, currentCompileResource);
    }

    @Override
    public void analysis() throws Exception {

    }
}

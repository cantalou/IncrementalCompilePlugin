package com.cantalou.gradle.android.incremental.analysis;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;

/**
 * @author cantalou
 * @date 2018-11-18 11:42
 */
public abstract class AbstractAnalysis<T> implements Analysis {

    protected static final Logger LOG = Logging.getLogger(Analysis.class);

    protected T preCompileResource;

    protected T currentCompileResource;

    protected String fullRebuildCause;


    public AbstractAnalysis(T preCompileResource, T currentCompileResource) {
        this.preCompileResource = preCompileResource;
        this.currentCompileResource = currentCompileResource;
    }

    public T getPreCompileResource() {
        return preCompileResource;
    }

    @Override
    public boolean isFullRebuildNeeded() {
        return fullRebuildCause != null;
    }

    @Override
    public String getFullRebuildCause() {
        return fullRebuildCause;
    }

    public void setFullRebuildCause(String fullRebuildCause) {
        this.fullRebuildCause = fullRebuildCause;
    }

    public void setPreCompileResource(T preCompileResource) {
        this.preCompileResource = preCompileResource;
    }

    public T getCurrentCompileResource() {
        return currentCompileResource;
    }

    public void setCurrentCompileResource(T currentCompileResource) {
        this.currentCompileResource = currentCompileResource;
    }
}

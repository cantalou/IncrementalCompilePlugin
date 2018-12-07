package com.m4399.gradle.incremental.analysis;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public abstract class AbstractAnalysis<T> implements Analysis {

    protected static final Logger LOG = Logging.getLogger(Analysis.class);

    protected T preCompileResource;

    protected T currentCompileResource;

    protected String fullRebuildCause;

    protected boolean analysed = false;

    public AbstractAnalysis(T preCompileResource, T currentCompileResource) {
        this.preCompileResource = preCompileResource;
        this.currentCompileResource = currentCompileResource;
    }

    public T getPreCompileResource() {
        return preCompileResource;
    }

    @Override
    public boolean isFullRebuildNeeded() throws Exception {
        ensureAnalysis();
        return fullRebuildCause != null;
    }

    @Override
    public String getFullRebuildCause() throws Exception {
        ensureAnalysis();
        return fullRebuildCause;
    }

    protected void ensureAnalysis() throws Exception{
        if (!analysed) {
            analysis();
            analysed = true;
        }
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

package com.m4399.gradle.incremental.analysis;

public interface Analysis {

    String getFullRebuildCause() throws Exception;

    boolean isFullRebuildNeeded() throws Exception;

    void analysis() throws Exception;
}

package com.cantalou.gradle.android.incremental.analysis;

/**
 * @author cantalou
 */
public interface Analysis {

    String getFullRebuildCause() throws Exception;

    boolean isFullRebuildNeeded() throws Exception;

    void analysis() throws Exception;
}

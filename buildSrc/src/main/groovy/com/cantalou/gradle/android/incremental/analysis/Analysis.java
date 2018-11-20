package com.cantalou.gradle.android.incremental.analysis;

/**
 * @author cantalou
 * @date 2018-11-18 11:21
 */
public interface Analysis {

    String getFullRebuildCause() throws Exception;

    boolean isFullRebuildNeeded() throws Exception;

    void analysis() throws Exception;
}

package com.cantalou.gradle.android.incremental.analysis;

/**
 * @author cantalou
 * @date 2018-11-18 11:21
 */
public interface Analysis {

    String getFullRebuildCause();

    boolean isFullRebuildNeeded() ;

    void analysis() throws Exception;
}

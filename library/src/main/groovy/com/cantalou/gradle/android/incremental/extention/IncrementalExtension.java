package com.cantalou.gradle.android.incremental.extention;

/**
 * @author cantalou
 *
 */
public class IncrementalExtension
{

    public static final String NAME = "incremental";

    /**
     * Auto enable preDexLibraries properties when only one device connected that sdk version large 21
     */
    public boolean disableAutoPreDex;
}

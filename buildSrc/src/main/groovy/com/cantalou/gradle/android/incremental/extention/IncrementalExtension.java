package com.cantalou.gradle.android.incremental.extention;

/**
 * @author LinZhiWei
 *
 */
public class IncrementalExtension
{

    public static final String NAME = "incremental";

    /**
     * Auto enable preDexLibraries properties when only one device connected that sdk version > 21
     */
    public boolean autoPreDex;
}

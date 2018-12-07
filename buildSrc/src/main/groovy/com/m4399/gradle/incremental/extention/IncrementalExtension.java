package com.m4399.gradle.incremental.extention;

public class IncrementalExtension
{

    public static final String NAME = "incremental";

    /**
     * Auto enable preDexLibraries properties when only one device connected that sdk version large 21
     */
    public boolean disableAutoPreDex;
}

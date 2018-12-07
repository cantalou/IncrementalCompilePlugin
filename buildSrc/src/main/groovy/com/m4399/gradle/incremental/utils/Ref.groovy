package com.m4399.gradle.incremental.utils

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class Ref {

    static def getValue(Object instance, Class clazz, String fieldName) throws IllegalAccessException {
        getField(clazz, fieldName, true).get(instance)
    }

    static def getField(Class cls, String fieldName, boolean forceAccess) {
        if (cls == null) {
            throw new IllegalArgumentException("The class must not be null")
        } else if (fieldName == null) {
            throw new IllegalArgumentException("The field name must not be null")
        } else {
            for (Class clazz = cls; clazz != null; clazz = clazz.getSuperclass()) {
                try {
                    Field field = clazz.getDeclaredField(fieldName)
                    if (!Modifier.isPublic(field.getModifiers())) {
                        if (!forceAccess) {
                            continue
                        }
                        field.setAccessible(true);
                    }
                    return field
                } catch (NoSuchFieldException var7) {
                }
            }
            throw new NoSuchFieldException(fieldName)
        }
    }
}


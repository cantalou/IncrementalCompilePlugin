package com.cantalou.gradle.android.incremental.analysis;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author cantalou
 * @date 2018-11-18 11:21
 */
public class ClassAnalysis extends AbstractAnalysis<Class> {

    public ClassAnalysis(Class preCompileResource, Class currentCompileResource) {
        super(preCompileResource, currentCompileResource);
    }

    @Override
    public void analysis() throws Exception {
        int modifierFlag = Modifier.STATIC | Modifier.FINAL;
        Field[] preCompileFields = preCompileResource.getDeclaredFields();
        for (Field preField : preCompileFields) {
            preField.setAccessible(true);
            if ((preField.getModifiers() & modifierFlag) != modifierFlag) {
                continue;
            }
            Class type = preField.getType();
            if (!(type.isPrimitive() || type == String.class)) {
                continue;
            }
            try {
                Field currentField = currentCompileResource.getDeclaredField(preField.getName());
                currentField.setAccessible(true);
                Object preValue = preField.get(null);
                Object currentValue = currentField.get(null);
                if (!preValue.equals(currentValue)) {
                    setFullRebuildCause("value of constant field '" + preField.getName() + "' was modified from " + preValue + " to " + currentValue);
                    break;
                }
            } catch (NoSuchFieldException e) {
                setFullRebuildCause("constant field '" + preField.getName() + "' was removed from " + preCompileResource.getName());
                break;
            }
        }
    }
}

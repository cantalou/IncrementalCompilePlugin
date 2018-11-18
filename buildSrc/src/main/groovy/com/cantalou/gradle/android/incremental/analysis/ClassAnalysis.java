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
        Field[] preCompileFields = getPreCompileResource().getDeclaredFields();
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
                Field currentField = getCurrentCompileResource().getDeclaredField(preField.getName());
                currentField.setAccessible(true);
                Object preValue = preField.get(null);
                Object currentValue = currentField.get(null);
                if (!preValue.equals(currentValue)) {
                    String cause = "field '" + preField.getName() + "' was modified from " + preValue + " to " + currentValue + "";
                    LOG.lifecycle(cause);
                    setFullRebuildCause(cause);
                    return;
                }
            } catch (NoSuchFieldException e) {
                LOG.lifecycle("${project.path}:${getName()} field '${preField.name}' was missing from ${currentCompileClass.name}");
                return;
            }
        }
    }
}

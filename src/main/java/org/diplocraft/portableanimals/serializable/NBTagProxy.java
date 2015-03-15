/*
 * The MIT License
 *
 * Copyright 2015 psygate (https://github.com/psygate/).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.diplocraft.portableanimals.serializable;

import java.io.DataInput;
import java.io.DataOutput;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;

/**
 *
 * @author psygate (https://github.com/psygate/)
 */
public class NBTagProxy {

    private final static Class<?>[] ECA = new Class<?>[0];
    private final static Object[] EOA = new Object[0];

    private final HandleProxy hproxy;
    private final Method setTag;
    private final Method getTag;
    private final Method loadTag;
    private final Method writeTag;
    private final Method emptyTag;
    private final Class<?> tagClass;

    public NBTagProxy(Object obj) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        hproxy = new HandleProxy(obj);
        tagClass = findNBTTagCompoundClass(hproxy.getHandle());
        emptyTag = findNBTTagCompoundEmptyMethod(tagClass);
        getTag = findGetTagMethod(hproxy.getHandle());
        setTag = findSetTagMethod(hproxy.getHandle());
        loadTag = findLoadTagMethod(tagClass);
        writeTag = findWriteTagMethod(tagClass);
        loadTag.setAccessible(true);
        writeTag.setAccessible(true);
    }

    private Method findGetTagMethod(Object handle) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        for (Method getCandidate : hproxy.getHandle().getClass().getDeclaredMethods()) {
            if (getCandidate.getParameterCount() == 1 && getCandidate.getParameterTypes()[0].equals(tagClass)) {
                Object testtag = tagClass.newInstance();
                getCandidate.invoke(hproxy.getHandle(), testtag);
                if (!(boolean) emptyTag.invoke(testtag, EOA)) {
                    return getCandidate;
                }
            }
        }

        throw new NoSuchElementException();
    }

    private Class<?> findNBTTagCompoundClass(Object handle) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        for (Method getCandidate : hproxy.getHandle().getClass().getDeclaredMethods()) {
            if (getCandidate.getParameterCount() == 1) {
                Class<?> param = getCandidate.getParameterTypes()[0];
                if (param.getSimpleName().equals("NBTTagCompound")) {
                    return param;
                }
            }
        }

        throw new NoSuchElementException();
    }

    private Method findNBTTagCompoundEmptyMethod(Class<?> tagClass) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
        return tagClass.getMethod("isEmpty", ECA);
    }

    private Method findSetTagMethod(Object handle) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        for (Method getCandidate : hproxy.getHandle().getClass().getDeclaredMethods()) {
            if (getCandidate.getParameterCount() == 1 && getCandidate.getParameterTypes()[0].equals(tagClass)) {
                Object testtag = tagClass.newInstance();
                getCandidate.invoke(hproxy.getHandle(), testtag);
                if ((boolean) emptyTag.invoke(testtag, EOA)) {
                    return getCandidate;
                }
            }
        }

        throw new NoSuchElementException();
    }

    private Method findLoadTagMethod(Class<?> tagClass) {
        for (Method candidate : tagClass.getDeclaredMethods()) {
            if (candidate.getName().endsWith("load")) {
                return candidate;
            }
        }
        Method best = null;
        for (Method candidate : tagClass.getDeclaredMethods()) {
            if (candidate.getParameterCount() == 1 || candidate.getParameterCount() == 2) {
                for (int i = 0; i < candidate.getParameterCount(); i++) {
                    Class<?> paramType = candidate.getParameterTypes()[i];
                    if (DataInput.class.isAssignableFrom(paramType)) {
                        if (best == null || !best.getName().endsWith("load")) {
                            best = candidate;
                        }
                    }
                }
            }
        }

        if (best == null) {
            throw new NoSuchElementException();
        } else {
            return best;
        }
    }

    private Method findWriteTagMethod(Class<?> tagClass) throws NoSuchMethodException {
//        return tagClass.getMethod("write", java.io.DataOutput.class);
        for (Method candidate : tagClass.getDeclaredMethods()) {
            if (candidate.getName().endsWith("write")) {
                return candidate;
            }
        }
        Method best = null;
        for (Method candidate : tagClass.getDeclaredMethods()) {
            if (candidate.getParameterCount() == 1) {
                Class<?> paramType = candidate.getParameterTypes()[0];
                if (DataOutput.class.isAssignableFrom(paramType)) {
                    if (best == null || !best.getName().endsWith("write")) {
                        best = candidate;
                    }
                }
            }
        }

        if (best == null) {
            throw new NoSuchElementException();
        } else {
            return best;
        }
    }

    public void write(DataOutput out, Object tagCarrier) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        HandleProxy proxy = new HandleProxy(tagCarrier);
        Object handle = proxy.getHandle();
        Object tag = tagClass.newInstance();
        getTag.invoke(handle, tag);
        writeTag.invoke(tag, out);
    }

    public void load(DataInput in, long readbytes, Object tagCarrier) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        HandleProxy proxy = new HandleProxy(tagCarrier);
        Object handle = proxy.getHandle();
        Object tag = tagClass.newInstance();
        loadTag.invoke(tag, in, 256, loadTag.getParameterTypes()[2].getConstructor(long.class).newInstance(Long.MAX_VALUE));
        setTag.invoke(handle, tag);
    }
}

package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import org.apache.jena.rdf.model.Resource;

import java.lang.reflect.Constructor;
import java.util.Arrays;

/**
 * A helper to work with {@link TranslationTableImpl}.
 * <p>
 * Created by @ssz on 01.10.2018.
 */
class TranslatorHelper {

    private static boolean hasImplementation(Class<?> test, Class<?> expected) {
        if (Arrays.asList(test.getInterfaces()).contains(expected)) {
            return true;
        }
        return test.getSuperclass() != null && hasImplementation(test.getSuperclass(), expected);
    }

    private static Translator construct(Constructor<?> constructor, Object... args) {
        try {
            return (Translator) constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Can't invoke constructor " + constructor, e);
        }
    }

    private static Constructor<?> getConstructor(Class<?> clazz, Class<?>... args) {
        try {
            return clazz.getConstructor(args);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Creates a new instance of {@link Translator} using the given full class path and resource instance.
     * It is expected that implementation has public constructor without arguments or with single {@link Resource} argument.
     *
     * @param classPath String, not {@code null}
     * @param resource  {@link Resource}, may be {@code null}
     * @return {@link Translator} instance.
     * @throws D2RQException in case it is no possible create an instance from the given params
     */
    static Translator createTranslator(String classPath, Resource resource) throws D2RQException {
        Class<?> clazz;
        try {
            clazz = Class.forName(classPath);
        } catch (ClassNotFoundException e) {
            throw new D2RQException("d2rq:javaClass not on classpath: " + classPath);
        }
        if (!hasImplementation(clazz, Translator.class)) {
            throw new D2RQException("d2rq:javaClass " + classPath + " must implement " + Translator.class.getName());
        }
        Constructor<?> res = getConstructor(clazz, Resource.class);
        if (res != null) {
            return construct(res, resource);
        }
        res = getConstructor(clazz);
        if (res != null) {
            return construct(res);
        }
        throw new D2RQException("No suitable public constructor found on d2rq:javaClass " + classPath);

    }
}

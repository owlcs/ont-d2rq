package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.csv.TranslationTableParser;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import org.apache.jena.rdf.model.Resource;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A helper to work with {@link TranslationTableImpl}.
 * <p>
 * Created by @ssz on 01.10.2018.
 */
class TranslatorHelper {

    /**
     * Creates a {@link Translator} from the given {@link TranslationTable}.
     * <p>
     * Although I don't know why D2RQ developers do not allow combining different kinds of translators,
     * the original logic has been preserved.
     * First checks for the class, then for file-url and only after all - parses RDF.
     * TODO: I am afraid D2RQ devs did it not from a great mind; if so, this it would be nice to fix this method.
     *
     * @param table {@link TranslationTable}, not {@code null}
     * @return {@link Translator}
     */
    static Translator getTranslator(TranslationTable table) {
        // predefined class:
        String javaClass = table.getJavaClass();
        if (javaClass != null) {
            return createTranslator(javaClass, table.asResource());
        }
        // file:
        String href = table.getHref();
        if (href != null) {
            return new Table(new TranslationTableParser(href).parseTranslations(),
                    TranslationTableParser.Row::first, TranslationTableParser.Row::second);
        }
        // rdf:
        return new Table(table.translations().collect(Collectors.toSet()),
                TranslationTable.Entry::getDatabaseValue, TranslationTable.Entry::getRDFValue);
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
    private static Translator createTranslator(String classPath, Resource resource) throws D2RQException {
        Class<?> clazz;
        try {
            clazz = Class.forName(classPath);
        } catch (ClassNotFoundException e) {
            throw new D2RQException("d2rq:javaClass not on classpath: " + classPath);
        }
        if (!Translator.class.isAssignableFrom(clazz)) {
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

    private static class Table implements Translator {
        private Map<String, String> translationsByDBValue = new HashMap<>();
        private Map<String, String> translationsByRDFValue = new HashMap<>();

        <X> Table(Collection<X> map, Function<X, String> key, Function<X, String> value) {
            for (X p : map) {
                translationsByDBValue.put(key.apply(p), value.apply(p));
                translationsByRDFValue.put(value.apply(p), key.apply(p));
            }
        }

        @Override
        public String toDBValue(String rdfValue) {
            return translationsByRDFValue.get(rdfValue);
        }

        @Override
        public String toRDFValue(String dbValue) {
            return translationsByDBValue.get(dbValue);
        }
    }
}

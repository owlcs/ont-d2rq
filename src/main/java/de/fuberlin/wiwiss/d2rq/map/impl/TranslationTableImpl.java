package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.csv.TranslationTableParser;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Resource;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Function;

/**
 * Represents a d2rq:TranslationTable.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @author zazi (http://github.com/zazi)
 */
@SuppressWarnings("WeakerAccess")
public class TranslationTableImpl extends MapObjectImpl implements TranslationTable {
    private Collection<P> translations = new ArrayList<>();
    private String javaClass;
    private String href;

    public TranslationTableImpl(Resource resource) {
        super(resource);
    }

    /**
     * Returns the number of defined mappings.
     *
     * @return int
     */
    @Override
    public int size() {
        return this.translations.size();
    }

    /**
     * Adds a translation mapping.
     *
     * @param dbValue  the value on the database side (usually coming from a DB column)
     * @param rdfValue the value on the RDF side (a string or URI)
     */
    @Override
    public void addTranslation(String dbValue, String rdfValue) {
        assertArgumentNotNull(dbValue, D2RQ.databaseValue, D2RQException.TRANSLATION_MISSING_DBVALUE);
        assertArgumentNotNull(rdfValue, D2RQ.rdfValue, D2RQException.TRANSLATION_MISSING_RDFVALUE);
        this.translations.add(new P(dbValue, rdfValue));
    }

    /**
     * Sets a translation class.
     * The translation class must implement the {@link Translator} interface.
     * This method will take care of generating an instance of the class.
     *
     * @param className name of a class implementing {@link Translator}
     */
    public void setJavaClass(String className) {
        assertNotYetDefined(this.javaClass, D2RQ.javaClass, D2RQException.TRANSLATIONTABLE_DUPLICATE_JAVACLASS);
        this.javaClass = className;
    }

    public void setHref(String href) {
        assertNotYetDefined(this.href, D2RQ.href, D2RQException.TRANSLATIONTABLE_DUPLICATE_HREF);
        this.href = href;
    }

    @Override
    public Translator translator() {
        validate();
        if (this.javaClass != null) {
            return instantiateJavaClass();
        }
        if (this.href != null) {
            return new TableTranslator(new TranslationTableParser(href).parseTranslations(),
                    TranslationTableParser.Row::first, TranslationTableParser.Row::second);
        }
        return new TableTranslator(this.translations, P::dbValue, P::rdfValue);
    }

    @Override
    public void validate() throws D2RQException {
        if (!this.translations.isEmpty() && this.javaClass != null) {
            throw new D2RQException("Can't combine d2rq:translation and d2rq:javaClass on " + this,
                    D2RQException.TRANSLATIONTABLE_TRANSLATION_AND_JAVACLASS);
        }
        if (!this.translations.isEmpty() && this.href != null) {
            throw new D2RQException("Can't combine d2rq:translation and d2rq:href on " + this,
                    D2RQException.TRANSLATIONTABLE_TRANSLATION_AND_HREF);
        }
        if (this.href != null && this.javaClass != null) {
            throw new D2RQException("Can't combine d2rq:href and d2rq:javaClass on " + this,
                    D2RQException.TRANSLATIONTABLE_HREF_AND_JAVACLASS);
        }
    }

    @Override
    public String toString() {
        return "d2rq:TranslationTable " + super.toString();
    }

    private Translator instantiateJavaClass() {
        try {
            Class<?> translatorClass = Class.forName(this.javaClass);
            if (!checkTranslatorClassImplementation(translatorClass)) {
                throw new D2RQException("d2rq:javaClass " + this.javaClass + " must implement " + Translator.class.getName());
            }
            if (hasConstructorWithArg(translatorClass)) {
                return invokeConstructorWithArg(translatorClass, asResource());
            }
            if (hasConstructorWithoutArg(translatorClass)) {
                return invokeConstructorWithoutArg(translatorClass);
            }
            throw new D2RQException("No suitable public constructor found on d2rq:javaClass " + this.javaClass);
        } catch (ClassNotFoundException e) {
            throw new D2RQException("d2rq:javaClass not on classpath: " + this.javaClass);
        }
    }

    /**
     * Checks whether the Translator class or a super class of it implements the Translator class interface.
     *
     * @param translatorClass a specific translator class or a more generic parent
     * @return true, if the currently checked translator class implements the Translator class interface
     */
    private boolean checkTranslatorClassImplementation(Class<?> translatorClass) {
        if (implementsTranslator(translatorClass)) {
            return true;
        }
        if (translatorClass.getSuperclass() == null) {
            return false;
        }
        return this.checkTranslatorClassImplementation(translatorClass
                .getSuperclass());
    }

    private boolean implementsTranslator(Class<?> aClass) {
        for (int i = 0; i < aClass.getInterfaces().length; i++) {
            if (aClass.getInterfaces()[i].equals(Translator.class)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConstructorWithArg(Class<?> aClass) {
        try {
            aClass.getConstructor(Resource.class);
            return true;
        } catch (NoSuchMethodException nsmex) {
            return false;
        }
    }

    private Translator invokeConstructorWithArg(Class<?> aClass, Resource r) {
        try {
            Constructor<?> c = aClass.getConstructor(Resource.class);
            return (Translator) c.newInstance(r);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean hasConstructorWithoutArg(Class<?> aClass) {
        try {
            aClass.getConstructor();
            return true;
        } catch (NoSuchMethodException nsmex) {
            return false;
        }
    }

    private Translator invokeConstructorWithoutArg(Class<?> aClass) {
        try {
            Constructor<?> c = aClass.getConstructor();
            return (Translator) c.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static class P {
        private final String dbValue;
        private final String rdfValue;

        public P(String dbValue, String rdfValue) {
            this.dbValue = Objects.requireNonNull(dbValue);
            this.rdfValue = Objects.requireNonNull(rdfValue);
        }

        public String dbValue() {
            return this.dbValue;
        }

        public String rdfValue() {
            return this.rdfValue;
        }

        @Override
        public int hashCode() {
            return this.dbValue.hashCode() ^ this.rdfValue.hashCode();
        }

        @Override
        public boolean equals(Object otherObject) {
            if (!(otherObject instanceof P)) return false;
            P other = (P) otherObject;
            return this.dbValue.equals(other.dbValue)
                    && this.rdfValue.equals(other.rdfValue);
        }

        @Override
        public String toString() {
            return "'" + this.dbValue + "'=>'" + this.rdfValue + "'";
        }
    }

    private class TableTranslator implements Translator {
        private Map<String, String> translationsByDBValue = new HashMap<>();
        private Map<String, String> translationsByRDFValue = new HashMap<>();

        <X> TableTranslator(Collection<X> map, Function<X, String> key, Function<X, String> value) {
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

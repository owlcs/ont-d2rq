package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.csv.TranslationTableParser;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Resource;

import java.util.*;
import java.util.function.Function;

/**
 * Represents a {@code d2rq:TranslationTable}.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @author zazi (http://github.com/zazi)
 */
@SuppressWarnings("WeakerAccess")
public class TranslationTableImpl extends MapObjectImpl implements TranslationTable {
    private Collection<Entry> translations = new ArrayList<>();
    private String javaClass;
    private String href;

    public TranslationTableImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
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
     * @return {@link TranslationTableImpl}
     */
    @Override
    public TranslationTableImpl addTranslation(String dbValue, String rdfValue) {
        assertArgumentNotNull(dbValue, D2RQ.databaseValue, D2RQException.TRANSLATION_MISSING_DBVALUE);
        assertArgumentNotNull(rdfValue, D2RQ.rdfValue, D2RQException.TRANSLATION_MISSING_RDFVALUE);
        this.translations.add(new Entry(dbValue, rdfValue));
        return this;
    }

    /**
     * Sets a translation class.
     * The translation class must implement the {@link Translator} interface.
     * This method will take care of generating an instance of the class.
     *
     * @param className name of a class implementing {@link Translator}
     * @return {@link TranslationTableImpl}
     */
    @Override
    public TranslationTableImpl setJavaClass(String className) {
        assertNotYetDefined(getJavaClass(), D2RQ.javaClass, D2RQException.TRANSLATIONTABLE_DUPLICATE_JAVACLASS);
        this.javaClass = className;
        return this;
    }

    @Override
    public String getJavaClass() {
        return javaClass;
    }

    @Override
    public TranslationTableImpl setHref(String href) {
        assertNotYetDefined(getHref(), D2RQ.href, D2RQException.TRANSLATIONTABLE_DUPLICATE_HREF);
        this.href = href;
        return this;
    }

    @Override
    public String getHref() {
        return href;
    }

    @Override
    public Translator asTranslator() {
        validate();
        String javaClass = getJavaClass();
        if (javaClass != null) {
            return TranslatorHelper.createTranslator(javaClass, asResource());
        }
        String href = getHref();
        if (href != null) {
            return new TableTranslator(new TranslationTableParser(href).parseTranslations(),
                    TranslationTableParser.Row::first, TranslationTableParser.Row::second);
        }
        return new TableTranslator(this.translations, Entry::dbValue, Entry::rdfValue);
    }

    @Override
    public void validate() throws D2RQException {
        // todo: Why just don't allow to combine different kinds of translators ?
        // todo: What it is - one more stupidity of the D2RQ developers or there is a deep meaning here ?
        String href = getHref();
        String javaClass = getJavaClass();
        if (!this.translations.isEmpty() && javaClass != null) {
            throw new D2RQException("Can't combine d2rq:translation and d2rq:javaClass on " + this,
                    D2RQException.TRANSLATIONTABLE_TRANSLATION_AND_JAVACLASS);
        }
        if (!this.translations.isEmpty() && href != null) {
            throw new D2RQException("Can't combine d2rq:translation and d2rq:href on " + this,
                    D2RQException.TRANSLATIONTABLE_TRANSLATION_AND_HREF);
        }
        if (href != null && javaClass != null) {
            throw new D2RQException("Can't combine d2rq:href and d2rq:javaClass on " + this,
                    D2RQException.TRANSLATIONTABLE_HREF_AND_JAVACLASS);
        }
    }

    @Override
    public String toString() {
        return "d2rq:TranslationTable " + super.toString();
    }

    public static class Entry {
        private final String dbValue;
        private final String rdfValue;

        public Entry(String dbValue, String rdfValue) {
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
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Entry)) return false;
            Entry p = (Entry) other;
            return this.dbValue.equals(p.dbValue) && this.rdfValue.equals(p.rdfValue);
        }

        @Override
        public String toString() {
            return String.format("'%s'=>'%s'", this.dbValue, this.rdfValue);
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

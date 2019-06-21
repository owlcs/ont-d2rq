package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.MapObject;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a {@code d2rq:TranslationTable}.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @author zazi (http://github.com/zazi)
 */
@SuppressWarnings("WeakerAccess")
public class TranslationTableImpl extends MapObjectImpl implements TranslationTable {

    public TranslationTableImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    @Override
    public EntryImpl createTranslation() {
        Resource res;
        resource.addProperty(D2RQ.translation, res = getModel().createResource());
        return asTranslation(res);
    }

    public EntryImpl asTranslation(Resource r) {
        return new EntryImpl(r, mapping);
    }

    @Override
    public Stream<Entry> translations() {
        return Iter.asStream(listTranslationResources().mapWith(this::asTranslation));
    }

    /**
     * Lists all {@code d2rq:translation} resources for the given db and rdf values
     * and this {@code d2rq:TranslationTable} as subject.
     *
     * @param dbValue  String, the literal value for the {@code d2rq:databaseValue} predicate
     * @param rdfValue String, either uri or literal value for the {@code d2rq:rdfValue} predicate
     * @return {@link ExtendedIterator} of {@link Resource}s
     */
    public ExtendedIterator<Resource> listTranslationResources(String dbValue, String rdfValue) {
        return listTranslationResources()
                .filterKeep(s -> s.hasProperty(D2RQ.databaseValue, dbValue))
                .filterKeep(s -> s.listProperties(D2RQ.rdfValue)
                        .mapWith(Statement::getObject)
                        .mapWith(EntryImpl::getString)
                        .toSet().contains(rdfValue));
    }

    /**
     * Lists all {@code d2rq:translation} resources for this {@code d2rq:TranslationTable} as subject.
     *
     * @return {@link ExtendedIterator} of {@link Resource}s
     */
    public ExtendedIterator<Resource> listTranslationResources() {
        return resource.listProperties(D2RQ.translation).mapWith(s -> s.getObject().asResource());
    }

    @Override
    public String getJavaClass() {
        return getString(D2RQ.javaClass);
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
        return setLiteral(D2RQ.javaClass, className);
    }

    @Override
    public String getHref() {
        return findURI(D2RQ.href).orElse(null);
    }

    @Override
    public TranslationTableImpl setHref(String href) {
        return setURI(D2RQ.href, href);
    }

    @Override
    public Translator asTranslator() {
        validate();
        return TranslatorHelper.getTranslator(this);
    }

    /**
     * Since D2RQ language allow both uri and literal nodes for the predicate {@code d2rq:rdfValue},
     * it is important to avoid such collisions
     *
     * @param e {@link Entry}
     */
    private void checkNoDuplicates(Entry e) {
        Set<Resource> res = listTranslationResources(e.getDatabaseValue(), e.getRDFValue()).toSet();
        if (res.size() != 1) {
            throw new D2RQException(e + ":: found duplicate d2rq:translation : " + res);
        }
    }

    @Override
    public void validate() throws D2RQException {
        Set<Entry> translations = translations()
                .peek(MapObject::validate)
                .peek(this::checkNoDuplicates)
                .collect(Collectors.toSet());

        Validator v = new Validator(this);
        Validator.ForProperty javaClass = v.forProperty(D2RQ.javaClass);
        if (javaClass.exists()) {
            javaClass.requireHasNoDuplicates(D2RQException.TRANSLATIONTABLE_DUPLICATE_JAVACLASS)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED)
                    .requireValidClassReference(Translator.class, D2RQException.UNSPECIFIED);
        }
        Validator.ForProperty href = v.forProperty(D2RQ.href);
        if (href.exists()) {
            href.requireHasNoDuplicates(D2RQException.TRANSLATIONTABLE_DUPLICATE_HREF)
                    .requireIsURI(D2RQException.UNSPECIFIED)
                    .requireIsValidURL(D2RQException.UNSPECIFIED);
        }
        if (!translations.isEmpty() && javaClass.exists()) {
            throw new D2RQException("Can't combine d2rq:translation and d2rq:javaClass on " + this,
                    D2RQException.TRANSLATIONTABLE_TRANSLATION_AND_JAVACLASS);
        }
        if (!translations.isEmpty() && href.exists()) {
            throw new D2RQException("Can't combine d2rq:translation and d2rq:href on " + this,
                    D2RQException.TRANSLATIONTABLE_TRANSLATION_AND_HREF);
        }
        if (href.exists() && javaClass.exists()) {
            throw new D2RQException("Can't combine d2rq:href and d2rq:javaClass on " + this,
                    D2RQException.TRANSLATIONTABLE_HREF_AND_JAVACLASS);
        }
    }

    @Override
    public String toString() {
        return "d2rq:TranslationTable " + super.toString();
    }

    public static class EntryImpl extends MapObjectImpl implements Entry {

        public EntryImpl(Resource resource, MappingImpl mapping) {
            super(resource, mapping);
        }

        /**
         * Gets node as string.
         * This is due to D2RQ allow both uri and blank nodes for the {@code d2rq:rdfValue} predicate.
         *
         * @param n {@link RDFNode}
         * @return String, either uri or literal lexical form
         */
        private static String getString(RDFNode n) {
            if (n.isURIResource())
                return n.asResource().getURI();
            if (n.isLiteral())
                return n.asLiteral().getString();
            throw new IllegalArgumentException("Can work only with uri or literal nodes: " + n);
        }

        @Override
        public EntryImpl setURI(String uri) {
            return setURI(D2RQ.rdfValue, uri);
        }

        @Override
        public EntryImpl setLiteral(String value) {
            return setLiteral(D2RQ.rdfValue, value);
        }

        @Override
        public EntryImpl setDatabaseValue(String value) {
            return setLiteral(D2RQ.databaseValue, value);
        }

        @Override
        public TranslationTable getTable() {
            List<Resource> res = getModel().listResourcesWithProperty(D2RQ.translation, resource).toList();
            if (res.size() != 1) throw new IllegalStateException("Can't find d2rq:TranslationTable for " + toString());
            return mapping.asTranslationTable(res.get(0));
        }

        @Override
        public String getRDFValue() {
            return findFirst(D2RQ.rdfValue, Statement::getObject)
                    .map(EntryImpl::getString)
                    .orElse(null);
        }

        @Override
        public String getDatabaseValue() {
            return getString(D2RQ.databaseValue);
        }

        @Override
        public void validate() throws D2RQException {
            Validator v = new Validator(this);
            v.forProperty(D2RQ.rdfValue)
                    .requireExists(D2RQException.TRANSLATION_MISSING_RDFVALUE)
                    .requireHasNoDuplicates(D2RQException.UNSPECIFIED)
                    .requireIsNotAnonymous(D2RQException.UNSPECIFIED);
            v.forProperty(D2RQ.databaseValue)
                    .requireExists(D2RQException.TRANSLATION_MISSING_DBVALUE)
                    .requireHasNoDuplicates(D2RQException.UNSPECIFIED)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        }

        @Override
        public String toString() {
            return String.format("d2rq:translation [%s <=> %s]", getDatabaseValue(), getRDFValue());
        }
    }
}

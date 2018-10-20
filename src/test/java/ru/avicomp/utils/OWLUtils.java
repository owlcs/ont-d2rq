package ru.avicomp.utils;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.model.OntEntity;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;

/**
 * Created by @ssz on 20.10.2018.
 */
public class OWLUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(OWLUtils.class);

    public static <X extends OntEntity> X findEntity(OntGraphModel m, Class<X> type, String shortForm) {
        X res = m.getOntEntity(type, m.expandPrefix(shortForm));
        Assert.assertNotNull("Can't find " + type.getSimpleName() + " " + shortForm, res);
        return res;
    }

    public static void validateOWLEntities(OntGraphModel m,
                                           int classes,
                                           int objectProperties,
                                           int dataProperties,
                                           int annotationProperties,
                                           int namedIndividuals,
                                           int anonymousIndividuals) {
        Assert.assertEquals(namedIndividuals, m.listNamedIndividuals()
                .peek(i -> LOGGER.debug("Named: {}", i)).count());
        Assert.assertEquals(anonymousIndividuals, m.ontObjects(OntIndividual.Anonymous.class)
                .peek(i -> LOGGER.debug("Anonymous: {}", i)).count());

        Assert.assertEquals(namedIndividuals + anonymousIndividuals, m.ontObjects(OntIndividual.class)
                .peek(i -> LOGGER.debug("Individual: {}", i)).count());

        Assert.assertEquals(classes, m.listClasses().peek(x -> LOGGER.debug("Class: {}", x)).count());

        Assert.assertEquals(annotationProperties, m.listAnnotationProperties()
                .peek(x -> LOGGER.debug("AnnotationProperty: {}", x)).count());
        Assert.assertEquals(objectProperties, m.listObjectProperties()
                .peek(x -> LOGGER.debug("ObjectProperty: {}", x)).count());
        Assert.assertEquals(dataProperties, m.listDataProperties()
                .peek(x -> LOGGER.debug("DatatypeProperty: {}", x)).count());
    }
}

package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.values.Translator;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface TranslationTable extends MapObject {

    int size();

    void addTranslation(String dbValue, String rdfValue);

    Translator translator();
}

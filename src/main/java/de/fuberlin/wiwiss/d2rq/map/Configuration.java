package de.fuberlin.wiwiss.d2rq.map;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface Configuration extends MapObject {

    void setServeVocabulary(boolean b);

    boolean getServeVocabulary();

    boolean getUseAllOptimizations();

    void setUseAllOptimizations(boolean b);
}

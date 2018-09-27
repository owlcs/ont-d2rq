package de.fuberlin.wiwiss.d2rq.map;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface Configuration extends MapObject {

    boolean getServeVocabulary();

    boolean getUseAllOptimizations();

    void setServeVocabulary(boolean b);

    void setUseAllOptimizations(boolean b);

}

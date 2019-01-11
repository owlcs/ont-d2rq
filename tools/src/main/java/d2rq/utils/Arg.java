package d2rq.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A command line argument that has been found specification.
 */
public class Arg {
    private String name;
    private String value;
    private List<String> values = new ArrayList<>();

    Arg(String name) {
        this.name = name;
    }

    void addValue(String v) {
        values.add(v);
    }

    String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    void setValue(String v) {
        value = v;
    }

    List<String> getValues() {
        return values;
    }

    boolean hasValue() {
        return value != null;
    }

    public boolean matches(ArgDecl decl) {
        return decl.getNames().contains(name);
    }

}

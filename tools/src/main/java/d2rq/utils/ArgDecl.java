package d2rq.utils;

import java.util.*;

/**
 * A command line argument specification.
 */
public class ArgDecl {
    private final boolean takesValue;
    private final Set<String> names;

    /**
     * Creates a declaration for a command argument.
     *
     * @param hasValue does it take a value or not?
     * @param names    Array of alias names
     */
    public ArgDecl(boolean hasValue, String... names) {
        List<String> list = Arrays.asList(names);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("At least one argument name must be given,");
        }
        this.takesValue = hasValue;
        this.names = new HashSet<>(list);
    }

    Iterator<String> names() {
        return names.iterator();
    }

    boolean takesValue() {
        return takesValue;
    }
}

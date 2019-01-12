package d2rq.utils;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * A command line argument specification.
 * TODO: see {@link d2rq.utils.CommandLine} notes.
 */
public class ArgDecl {
    private boolean takesValue;
    private Set<String> names = new HashSet<>();
    private List<BiConsumer<String, String>> argHooks = new ArrayList<>();

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     */
    public ArgDecl(boolean hasValue) {
        takesValue = hasValue;
    }

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     * @param name     Name of argument
     */
    public ArgDecl(boolean hasValue, String name) {
        this(hasValue);
        addName(name);
    }

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     * @param name     Name of argument
     * @param handler  BiConsumer&lt;String, String;gt;
     */
    public ArgDecl(boolean hasValue, String name, BiConsumer<String, String> handler) {
        this(hasValue);
        addName(name);
        addHook(handler);
    }

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     * @param name1    Name of argument
     * @param name2    Name of argument
     */
    public ArgDecl(boolean hasValue, String name1, String name2) {
        this(hasValue);
        addName(name1);
        addName(name2);
    }

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     * @param name1    Name of argument
     * @param name2    Name of argument
     * @param handler  BiConsumer&lt;String, String;gt;
     */
    public ArgDecl(boolean hasValue, String name1, String name2, BiConsumer<String, String> handler) {
        this(hasValue);
        addName(name1);
        addName(name2);
        addHook(handler);
    }

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     * @param name1    Name of argument
     * @param name2    Name of argument
     * @param name3    Name of argument
     */
    public ArgDecl(boolean hasValue, String name1, String name2, String name3) {
        this(hasValue);
        addName(name1);
        addName(name2);
        addName(name3);
    }

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     * @param name1    Name of argument
     * @param name2    Name of argument
     * @param name3    Name of argument
     * @param handler  BiConsumer&lt;String, String;gt;
     */
    public ArgDecl(boolean hasValue, String name1, String name2, String name3, BiConsumer<String, String> handler) {
        this(hasValue);
        addName(name1);
        addName(name2);
        addName(name3);
        addHook(handler);
    }

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     * @param name1    Name of argument
     * @param name2    Name of argument
     * @param name3    Name of argument
     * @param name4    Name of argument
     */
    public ArgDecl(boolean hasValue, String name1, String name2, String name3, String name4) {
        this(hasValue);
        addName(name1);
        addName(name2);
        addName(name3);
        addName(name4);
    }

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     * @param name1    Name of argument
     * @param name2    Name of argument
     * @param name3    Name of argument
     * @param name4    Name of argument
     * @param handler  BiConsumer&lt;String, String;gt;
     */
    public ArgDecl(boolean hasValue, String name1, String name2, String name3, String name4, BiConsumer<String, String> handler) {
        this(hasValue);
        addName(name1);
        addName(name2);
        addName(name3);
        addName(name4);
        addHook(handler);
    }

    /**
     * Create a declaration for a command argument.
     *
     * @param hasValue Does it take a value or not?
     * @param name1    Name of argument
     * @param name2    Name of argument
     * @param name3    Name of argument
     * @param name4    Name of argument
     * @param name5    Name of argument
     * @param handler  BiConsumer&lt;String, String;gt;
     */
    public ArgDecl(boolean hasValue, String name1, String name2, String name3, String name4, String name5, BiConsumer<String, String> handler) {
        this(hasValue);
        addName(name1);
        addName(name2);
        addName(name3);
        addName(name4);
        addName(name5);
        addHook(handler);
    }

    public static String canonicalForm(String str) {
        if (str.startsWith("--"))
            return str.substring(2);

        if (str.startsWith("-"))
            return str.substring(1);

        return str;
    }

    public void addName(String name) {
        name = canonicalForm(name);
        names.add(name);
    }

    public Set<String> getNames() {
        return names;
    }

    // Callback model

    public Iterator<String> names() {
        return names.iterator();
    }

    public void addHook(BiConsumer<String, String> argHandler) {
        argHooks.add(argHandler);
    }

    public void trigger(Arg arg) {
        argHooks.forEach(action -> action.accept(arg.getName(), arg.getValue()));
    }

    public boolean takesValue() {
        return takesValue;
    }

    public boolean matches(Arg a) {
        for (String n : names) {
            if (a.getName().equals(n)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(String arg) {
        arg = canonicalForm(arg);
        return names.contains(arg);
    }
}

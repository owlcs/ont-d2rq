package de.fuberlin.wiwiss.d2rq.map;

/**
 * A technical interface to provide access to {@link Database}.
 * Applicable for {@link ClassMap} and {@link DownloadMap}.
 * <p>
 * Created by @ssz on 27.09.2018.
 *
 * @param <R> subtype of {@link MapObject}
 */
interface HasDatabase<R extends MapObject> {

    /**
     * Sets the given database to this {@link MapObject MapObject}.
     * This method creates the statement {@code _:r d2rq:dataStorage _:d},
     * where {@code _:r} is this resource ({@link MapObject#asResource()}) and {@code _:d} is the specified database.
     * Note: a previously associated database link will be suppressed.
     *
     * @param database {@link Database}, not {@code null}
     * @return {@link R} to allow cascading calls
     */
    R setDatabase(Database database);

    /**
     * Finds the {@link Database} that is attached to this mapping resource on predicate {@code d2rq:dataStorage}.
     *
     * @return {@link Database} or {@code null}
     */
    Database getDatabase();
}

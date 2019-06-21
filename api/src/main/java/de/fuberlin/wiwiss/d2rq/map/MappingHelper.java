package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.download.DownloadContentQuery;
import de.fuberlin.wiwiss.d2rq.map.impl.DatabaseImpl;
import de.fuberlin.wiwiss.d2rq.map.impl.DownloadMapImpl;
import de.fuberlin.wiwiss.d2rq.map.impl.MappingImpl;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

/**
 * Various utility methods to work with {@link Mapping D2RQ Mapping Model} and with some its components.
 * Created by @ssz on 17.10.2018.
 *
 * @see MappingFactory
 * @see MapParser
 */
public class MappingHelper {

    /**
     * Represents the given {@link Mapping D2RQ Mapping Model} as a {@link ConnectingMapping}.
     *
     * @param m {@link Mapping}, not {@code null}
     * @return {@link ConnectingMapping}, not {@code null}
     * @throws ClassCastException if wrong instance
     * @see #asMapping(ConnectingMapping)
     */
    public static ConnectingMapping asConnectingMapping(Mapping m) {
        return (ConnectingMapping) m;
    }

    /**
     * Represents the given {@link ConnectingMapping} as a {@link Mapping D2RQ Mapping Model}.
     *
     * @param m {@link ConnectingMapping}, not {@code null}
     * @return {@link Mapping}, not {@code null}
     * @throws ClassCastException if wrong instance
     * @see #asConnectingMapping(Mapping)
     */
    public static Mapping asMapping(ConnectingMapping m) {
        return (Mapping) m;
    }

    /**
     * Inserts the {@code ConnectedDB} into the mapping.
     * Original comment:
     * This is a hack where we can pass a pre-existing ConnectedDB that
     * will be used by the mapping {@link Database},
     * so we avoid that the {@code Database} opens another connection to the same DB.
     *
     * @param m  {@link Mapping}, not {@code null}
     * @param db {@link ConnectedDB}, not {@code null}
     * @throws IllegalArgumentException in case no {@code d2rq:Database} is found for the given {@code ConnectedDB}
     */
    public static void useConnectedDB(Mapping m, ConnectedDB db) throws IllegalArgumentException {
        DatabaseImpl res = (DatabaseImpl) m.getDatabase(db.getJdbcURL());
        ((MappingImpl) m).registerConnectedDB(res, db);
    }

    /**
     * Fetches the {@link ConnectedDB} for the specified {@link Database d2rq:Database}.
     *
     * @param db {@link Database}, not {@code null}
     * @return {@link ConnectedDB}, not {@code null}
     */
    public static ConnectedDB getConnectedDB(Database db) {
        DatabaseImpl d = (DatabaseImpl) db;
        return d.getMapping().getConnectedDB(d);
    }

    /**
     * Creates a query helper that evaluates the given {@link DownloadMap} and the particular URI,
     * returning either the content, or {@code null} if the URI isn't applicable for the download map
     * or there is nothing in the table for the value.
     *
     * @param downloadMap {@link DownloadMap}, not {@code null}, the download map to be queried
     * @param uri         String, not {@code null}, the URI whose content is desired
     * @return {@link DownloadContentQuery}
     */
    public static DownloadContentQuery getDownloadContentQuery(DownloadMap downloadMap, String uri) {
        return new DownloadContentQuery((DownloadMapImpl) downloadMap, uri);
    }
}

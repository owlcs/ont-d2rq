package ru.avicomp.ontapi.conf;

import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.semanticweb.owlapi.model.IRI;
import ru.avicomp.ontapi.D2RQGraphDocumentSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Created by @szuev on 29.03.2018.
 */
public enum ConnectionData {
    /**
     * to set up use <a href='file:doc/example/iswc-mysql.sql'>iswc-mysql.sql</a>
     */
    MYSQL,
    /**
     * to set up use <a href='file:doc/example/iswc-postgres.sql'>iswc-postgres.sql</a>
     */
    POSTGRES,;

    public static final IRI DEFAULT_BASE_IRI = IRI.create("http://d2rq.avc.ru/test/");

    private static final Properties PROPERTIES = load("/db.properties");
    private IRI base;

    public IRI getBaseIRI() {
        if (base != null) return base;
        String str = PROPERTIES.getProperty(prefix() + "uri");
        if (!str.endsWith("/")) str += "/";
        return base = IRI.create(str);
    }

    public IRI getIRI(String dbName) {
        return IRI.create(getBaseIRI() + Objects.requireNonNull(dbName));
    }

    public String getUser() {
        return PROPERTIES.getProperty(prefix() + "user");
    }

    public String getPwd() {
        return PROPERTIES.getProperty(prefix() + "password");
    }

    private String prefix() {
        return String.format("%s.", name().toLowerCase());
    }

    public D2RQGraphDocumentSource toDocumentSource(String dbName) {
        return toDocumentSource(DEFAULT_BASE_IRI, dbName);
    }

    public D2RQGraphDocumentSource toDocumentSource(IRI base, String dbName) {
        return D2RQGraphDocumentSource.create(base, getIRI(dbName), getUser(), getPwd());
    }


    public IRI toIRI(String uri) {
        return IRI.create(MYSQL.equals(this) ? uri : uri.toLowerCase());
    }

    public ConnectedDB toConnectedDB() {
        return new ConnectedDB(getBaseIRI().getIRIString(), getUser(), getPwd());
    }

    public ConnectedDB toConnectedDB(String dbName) {
        return new ConnectedDB(getIRI(dbName).getIRIString(), getUser(), getPwd());
    }

    public static List<ConnectionData> asList() {
        return Arrays.asList(values());
    }

    /**
     * Loads properties, first from System, then from file.
     *
     * @param file path
     * @return {@link Properties}
     */
    public static Properties load(String file) {
        Properties fromFile = new Properties();
        try (InputStream in = ConnectionData.class.getResourceAsStream(file)) {
            fromFile.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Properties res = new Properties(fromFile);
        System.getProperties().forEach((key, val) -> {
            if (!(key instanceof String)) return;
            String str = (String) key;
            if (Arrays.stream(values()).map(ConnectionData::prefix).anyMatch(str::startsWith)) {
                res.put(key, val);
            }
        });
        return res;
    }
}

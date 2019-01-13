package ru.avicomp.d2rq.conf;

import de.fuberlin.wiwiss.d2rq.map.Mapping;

/**
 * Created by @szz on 18.10.2018.
 */
public enum ISWCData {
    MYSQL(ConnectionData.MYSQL) {
        @Override
        public String getResourcePath() {
            return "/mapping-iswc.mysql.ttl";
        }
    },
    POSTGRES(ConnectionData.POSTGRES) {
        @Override
        public String getResourcePath() {
            return "/mapping-iswc.postrges.ttl";
        }
    },
    ;
    private final ConnectionData data;

    ISWCData(ConnectionData data) {
        this.data = data;
    }

    public abstract String getResourcePath();

    public Mapping loadMapping() {
        return loadMapping(null);
    }

    public ConnectionData getConnection() {
        return data;
    }

    public Mapping loadMapping(String baseURI) {
        return data.loadMapping(getResourcePath(), null, baseURI);
    }


}

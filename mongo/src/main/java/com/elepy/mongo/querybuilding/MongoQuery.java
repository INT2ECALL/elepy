package com.elepy.mongo.querybuilding;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Stream;

public class MongoQuery {

    private final MongoSearch mongoSearch;
    private final MongoFilters mongoFilters;


    public MongoQuery(MongoSearch mongoSearch, MongoFilters mongoFilters) {
        this.mongoSearch = mongoSearch;
        this.mongoFilters = mongoFilters;
    }

    public String compile() {

        if (mongoFilters.getMongoFilterTemplates().size() == 0 && (mongoSearch.getQuery() == null || mongoSearch.getQuery().isEmpty())) {
            return "{}";
        }

        if (mongoSearch.getQuery() == null || mongoSearch.getQuery().isEmpty()) {
            return mongoFilters.compile();
        }
        if (mongoFilters.getMongoFilterTemplates().size() == 0) {
            return mongoSearch.compile();
        }
        String join = String.join(",", mongoSearch.compile(), mongoFilters.compile());


        return String.format("{$and: [%s]}", join);
    }

    public Serializable[] getParameters() {
        return Stream.concat(
                Arrays.stream(mongoSearch.getParameters()),
                Arrays.stream(mongoFilters.getParameters())
        ).toArray(Serializable[]::new);
    }
}

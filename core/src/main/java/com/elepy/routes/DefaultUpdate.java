package com.elepy.routes;

import com.elepy.Elepy;
import com.elepy.concepts.IntegrityEvaluatorImpl;
import com.elepy.concepts.ObjectEvaluator;
import com.elepy.concepts.ObjectUpdateEvaluatorImpl;
import com.elepy.dao.Crud;
import com.elepy.exceptions.RestErrorMessage;
import com.elepy.models.FieldType;
import com.elepy.utils.ClassUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import spark.Request;
import spark.Response;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.util.*;

public class DefaultUpdate<T> implements RouteHandler<T> {

    private T before;

    public T before() {
        if (before == null) {
            throw new IllegalStateException("Before not yet set!");
        }
        return before;
    }

    public T update(Request request, Response response, Crud<T> dao, Elepy elepy, List<ObjectEvaluator<T>> objectEvaluators, Class<T> clazz) throws Exception {
        String body = request.body();


        Optional<T> before = dao.getById(request.params("id"));

        if (!before.isPresent()) {
            response.status(404);
            throw new RestErrorMessage("No object found with this ID");
        }
        final T updated;

        if (request.requestMethod().equals("PUT")) {
            if (body.startsWith("{")) {
                updated = elepy.getObjectMapper().readValue(body, clazz);

            } else {
                updated = objectFromMaps(new HashMap<>(), splitQuery(request.body()), elepy.getObjectMapper(), clazz);
            }
        } else {
            if (body.startsWith("{")) {
                final Map<String, Object> beforeMap = elepy.getObjectMapper().convertValue(before.get(), Map.class);
                final Map<String, Object> changesMap = elepy.getObjectMapper().readValue(request.body(), Map.class);

                changesMap.put("id", ClassUtils.getId(before.get()).get());
                updated = objectFromMaps(beforeMap, changesMap, elepy.getObjectMapper(), clazz);
            } else {
                updated = setParamsOnObject(request, elepy.getObjectMapper(), before.get());
            }
        }

        this.before = before.get();
        ObjectUpdateEvaluatorImpl<T> updateEvaluator = new ObjectUpdateEvaluatorImpl<T>();

        updateEvaluator.evaluate(before.get(), updated);

        for (ObjectEvaluator<T> objectEvaluator : objectEvaluators) {
            if (updated != null) {
                objectEvaluator.evaluate(updated, clazz);

            }
        }

        new IntegrityEvaluatorImpl<T>().evaluate(updated, dao);
        dao.update(updated);
        response.status(200);
        response.body("OK");
        return updated;
    }

    public static Map<String, Object> splitQuery(String body) throws UnsupportedEncodingException {
        Map<String, Object> queryPairs = new LinkedHashMap<>();
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            queryPairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return queryPairs;
    }


    public T objectFromMaps(Map<String, Object> objectAsMap, Map<String, Object> fieldsToAdd, ObjectMapper objectMapper, Class cls) {
        for (String s : fieldsToAdd.keySet()) {

            final Optional<Field> fieldWithName = ClassUtils.findFieldWithName(cls, s);

            if (fieldWithName.isPresent()) {
                Field field = fieldWithName.get();
                FieldType fieldType = FieldType.guessType(field);
                if (fieldType.isPrimitive()) {
                    objectAsMap.put(s, fieldsToAdd.get(s));
                }

            }
        }
        return (T) objectMapper.convertValue(objectAsMap, cls);
    }


    public T setParamsOnObject(Request request, ObjectMapper objectMapper, T object) throws UnsupportedEncodingException {

        Map<String, Object> map = objectMapper.convertValue(object, Map.class);
        Map<String, Object> splitQuery = splitQuery(request.body());

        return (T) objectFromMaps(map, splitQuery, objectMapper, object.getClass());
    }

    @Override
    public void handle(Request request, Response response, Crud<T> dao, Elepy elepy, List<ObjectEvaluator<T>> objectEvaluators, Class<T> clazz) throws Exception {
        this.update(request, response, dao, elepy, objectEvaluators, clazz);
    }
}

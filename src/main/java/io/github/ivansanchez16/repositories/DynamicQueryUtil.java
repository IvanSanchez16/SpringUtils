package io.github.ivansanchez16.repositories;

import lombok.experimental.UtilityClass;

import jakarta.persistence.Query;
import jakarta.persistence.criteria.*;
import java.lang.reflect.Field;

import java.util.*;

@UtilityClass
class DynamicQueryUtil {

    /**
     * Adds order criteria in CriteriaQuery based on sort_by param
     *
     * @param criteriaBuilder CriteriaBuilder object
     * @param criteriaQuery CriteriaQuery object
     * @param root Root object
     * @param params Map of RequestParams
     * @param <T> Type of root entity class
     */
    public <T> void addSortedValues(CriteriaBuilder criteriaBuilder, CriteriaQuery<T> criteriaQuery, Root<T> root, Map<String, Object> params) {
        if (params.containsKey("sort_by")) {
            String[] att;

            final String sortBy = params.get("sort_by").toString();
            final List<String> listSortBy = List.of(sortBy.split(","));
            final List<Order> orders = new ArrayList<>(listSortBy.size());

            for (String attSortBy: listSortBy) {
                att = attSortBy.split("\\.");
                if (att.length == 0)
                    continue;

                if (att.length == 1 || att[1].equals("asc")) {
                    orders.add( criteriaBuilder.asc( root.get(att[0]) ) );
                } else if (att[1].equals("desc")){
                    orders.add( criteriaBuilder.desc( root.get(att[0]) ) );
                }
            }

            if (!orders.isEmpty()) {
                criteriaQuery.orderBy( orders );
            }
        }
    }

    /**
     * Adds first result and max results for a query if page params are found.
     * Defaults values are first page and 10 page size
     *
     * @param query Query object to add pageable attributes
     * @param params Map of RequestParams (Name of pageable attributes are page and page_size)
     * @throws NumberFormatException If pageable attributes are not numeric
     */
    public void addPageableParams(Query query, Map<String, Object> params) throws NumberFormatException{
        final int pageSize;
        final int pageNo;

        if (params.containsKey("page_size") && params.containsKey("page")) {
            pageSize = Integer.parseInt( params.get("page_size").toString() );
            pageNo = Integer.parseInt( params.get("page").toString() );
        } else {
            // Defaults values are first page and 10 page size
            pageNo = 1;
            pageSize = 10;
        }

        query.setFirstResult( (pageNo - 1) * pageSize );
        query.setMaxResults( pageSize );
    }

    /**
     * Generate a new Map object with the params that are in entity's attributes and cast to
     * the attribute type
     *
     * @param original The Map of RequestParams
     * @param clazz Entity to be queried
     * @return New Map object cleaned with values cast
     * @param <T> Entity class type
     */
    public <T> Map<String, Object> prepareWhereParams(Map<String, Object> original, Class<T> clazz) {
        Field[] classFields = clazz.getDeclaredFields();
        Map<String, Object> preparedMap = new HashMap<>();

        original.forEach((attribute, value) -> {
            final String newAttribute;

            if (attribute.contains("_")) {
                newAttribute = cleanAttribute(attribute);
            } else {
                newAttribute = attribute;
            }

            final Field field = findByName(classFields, newAttribute);
            if (field == null) return;

            if (value.toString().contains(",")) {
                List<Object> values = List.of(value.toString().split(","));
                values = listCastTo(field.getType(), values);

                preparedMap.put(newAttribute, values);
            } else {
                preparedMap.put(newAttribute, castTo(field.getType(), value));
            }
        });

        return preparedMap;
    }

    public Predicate groupPredicateList(CriteriaBuilder cb, List<Predicate> predicateList) {
        final Predicate predicate = predicateList.remove(0);
        if (predicateList.isEmpty()) {
            return predicate;
        }

        return cb.and(predicate, groupPredicateList(cb, predicateList));
    }

    public <T> Path<T> groupPathList(Path<T> root, List<String> pathList) {
        final String attribute = pathList.remove(0);
        if (pathList.isEmpty()) {
            return root.get(attribute);
        }

        return groupPathList(root.get(attribute), pathList);
    }

    private Field findByName(Field[] fields, String name) {
        String[] attributeList = name.split("\\.");
        String attributeName = attributeList[0];

        for (Field field : fields) {
            if (field.getName().equalsIgnoreCase(attributeName)) {
                if (attributeList.length > 1) {
                    final String restOfString = name.substring( name.indexOf(attributeName)+attributeName.length()+1 );
                    return findByName(field.getType().getDeclaredFields(), restOfString);
                } else {
                    return field;
                }
            }
        }

        return null;
    }

    private String cleanAttribute(String oldAttribute) {
        final StringBuilder sb = new StringBuilder();
        boolean flag = false;

        for (int i = 0; i < oldAttribute.length(); i++) {
            if (oldAttribute.charAt(i) == '_') {
                flag = true;
                continue;
            }

            if (flag) {
                sb.append( (oldAttribute.charAt(i)+"").toUpperCase(Locale.ROOT) );
                flag = false;
            } else {
                sb.append( oldAttribute.charAt(i) );
            }
        }

        return sb.toString();
    }

    private <T> Object castTo(Class<T> clazz, Object value) {
        final String[] nameClass = clazz.getTypeName().split("\\.");
        final String sValue = value.toString();

        return switch (nameClass[nameClass.length-1]) {
            case "Boolean" -> Boolean.valueOf(sValue);
            case "Integer" -> Integer.parseInt(sValue);
            case "Short" -> Short.parseShort(sValue);
            case "Long" -> Long.parseLong(sValue);
            case "UUID" -> UUID.fromString(sValue);
            default -> sValue;
        };
    }

    private <T> List<Object> listCastTo(Class<T> clase, List<Object> values) {
        List<Object> newList = new ArrayList<>(values.size());

        for (final Object value : values) {
            newList.add(castTo(clase, value));
        }

        return newList;
    }
}

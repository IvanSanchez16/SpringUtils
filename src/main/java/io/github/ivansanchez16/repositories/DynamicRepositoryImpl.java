package io.github.ivansanchez16.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.*;

/**
 * DynamicRepositoryImpl
 *
 * Clase para consultar por atributos dinámicamente
 */
class DynamicRepositoryImpl<T, K> implements DynamicRepository<T, K> {

    private static final String HINT_DISTINCT_PASS_THROUGH = "hibernate.query.passDistinctThrough";

    private final CriteriaBuilder cb;

    private final EntityManager entityManager;

    public DynamicRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.cb = entityManager.getCriteriaBuilder();
    }

    @Override
    public CriteriaQuery<T> generateQuery(Class<T> clase) {
        return cb.createQuery(clase);
    }

    @Override
    public PageQuery<T> findByParams(Map<String, Object> params, Class<T> clase,
                                     CriteriaQuery<T> cQuery, Root<T> root)
    {
        Map<String, Object> whereParams = DynamicQueryUtil.prepareWhereParams(params, clase);
        final List<K> firstElementsKeys = findFirstElements(whereParams, params, clase);

        cQuery.select(root).distinct(true);
        DynamicQueryUtil.addSortedValues(cb, cQuery, root, params);

        final List<T> finalRows = findElementsWithJoin(firstElementsKeys, clase, cQuery, root);

        return new PageQuery<>(countElements(clase, whereParams), finalRows);
    }

    @Override
    public PageQuery<T> findByParams(Map<String, Object> params, Class<T> clase) {
        CriteriaQuery<T> cQuery = cb.createQuery(clase);
        Root<T> root = cQuery.from(clase);

        return findByParams(params, clase, cQuery, root);
    }

    @Override
    public Optional<T> findByIdWithJoins(K key, Class<T> clase, CriteriaQuery<T> cQuery, Root<T> root) {
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<K> findFirstElements(Map<String, Object> whereParams, Map<String, Object> params, Class<T> clase) {
        CriteriaQuery<T> cQuery = cb.createQuery(clase);
        Root<T> root = cQuery.from(clase);
        cQuery.select( root.get(getIdProperty(clase)) ).distinct(true);

        DynamicQueryUtil.addSortedValues(cb, cQuery, root, params);

        if (whereParams.isEmpty()) {
            Query query = entityManager.createQuery(cQuery);
            query.setHint(HINT_DISTINCT_PASS_THROUGH,
                    false);
            DynamicQueryUtil.addPageableParams(query, params);

            return query.getResultList();
        }

        List<Predicate> predicates = getPredicates(root, whereParams);

        cQuery.where( DynamicQueryUtil.groupPredicateList(cb, predicates) );

        Query query = entityManager.createQuery(cQuery);
        query.setHint(HINT_DISTINCT_PASS_THROUGH,
                false);
        DynamicQueryUtil.addPageableParams(query, params);

        return query.getResultList();
    }

    private Long countElements(Class<T> clase, Map<String, Object> whereParams) {
        CriteriaQuery<Long> cQuery = cb.createQuery(Long.class);
        Root<T> root = cQuery.from(clase);
        cQuery.select( cb.count(root) );

        List<Predicate> predicates = getPredicates(root, whereParams);
        cQuery.where( DynamicQueryUtil.groupPredicateList(cb, predicates) );

        return entityManager.createQuery(cQuery).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<Predicate> getPredicates(Path<T> root, Map<String, Object> whereParams) {
        List<Predicate> predicates = new ArrayList<>();
        whereParams.forEach((key, value) -> {
            String[] paths = key.split("\\.");
            Path<T> path = DynamicQueryUtil.groupPathList( root, new ArrayList<>(List.of(paths)) );

            if (value instanceof List) {
                CriteriaBuilder.In<Object> corePredicateIn = cb.in( path );
                ((List<Object>) value).forEach(corePredicateIn::value);

                predicates.add(corePredicateIn);
            } else {
                predicates.add(cb.equal( path , value));
            }
        });

        return predicates;
    }

    @SuppressWarnings("unchecked")
    private List<T> findElementsWithJoin(List<K> listOfKey, Class<T> clase, CriteriaQuery<T> cQuery, Root<T> root) {
        CriteriaBuilder.In<Object> corePredicateIn = cb.in( root.get(getIdProperty(clase)) );
        listOfKey.forEach(corePredicateIn::value);
        cQuery.where(corePredicateIn);

        Query query = entityManager.createQuery(cQuery);
        query.setHint(HINT_DISTINCT_PASS_THROUGH,
                false);

        return query.getResultList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String getIdProperty(Class entityClass) {
        String idProperty = null;
        Metamodel metamodel = entityManager.getMetamodel();
        EntityType entity = metamodel.entity(entityClass);

        Set<SingularAttribute> singularAttributes = entity.getSingularAttributes();
        for (SingularAttribute singularAttribute : singularAttributes) {
            if (singularAttribute.isId()){
                idProperty=singularAttribute.getName();
                break;
            }
        }

        if(idProperty==null)
            throw new NullPointerException("No se encontró el Id de la entidad");

        return idProperty;
    }
}

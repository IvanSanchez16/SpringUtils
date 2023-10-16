package io.github.ivansanchez16.repositories;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Map;
import java.util.Optional;

public interface DynamicRepository<T, K> {

    CriteriaQuery<T> generateQuery(Class<T> clase);

    /**
     * Método que consulta de manera dinámica una tabla (entidad) por N atributos
     *
     * @param params Un map donde las key son los nombres de los atributos y los values los valores para armar WHERE
     * @param clase La clase de la cual se está realizando la consulta
     * @return En formato de lista la consulta realizada
     */
    PageQuery<T> findByParams(Map<String, Object> params, Class<T> clase, CriteriaQuery<T> cQuery, Root<T> root);

    /**
     * Método que consulta de manera dinámica una tabla (entidad) por N atributos
     *
     * @param params Un map donde las key son los nombres de los atributos y los values los valores para armar WHERE
     * @param clase La clase de la cual se está realizando la consulta
     * @return En formato de lista la consulta realizada
     */
    PageQuery<T> findByParams(Map<String, Object> params, Class<T> clase);

    Optional<T> findByIdWithJoins(K key, Class<T> clase, CriteriaQuery<T> cQuery, Root<T> root);
}

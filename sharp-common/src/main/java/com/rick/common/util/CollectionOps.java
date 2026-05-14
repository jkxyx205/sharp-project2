package com.rick.common.util;

import com.rick.common.function.SFunction;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Rick.Xu
 * @date 2026/5/3 20:12
 */
@UtilityClass
public class CollectionOps {

    public static <E> Optional<E> expectedAsOptional(Collection<E> collection) {
        if (CollectionUtils.isEmpty(collection)) {
            return Optional.empty();
        }

        if (collection.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, collection.size());
        }

        return Optional.ofNullable(collection.iterator().next());
    }

    public static <R, T> Map<R, T> map(Collection<T> collection, SFunction<T, R> function) {
        return collection.stream().collect(Collectors.toMap(t -> function.isMethodReference() ? (R) new BeanWrapperImpl(t).getPropertyValue(function.getPropertyName()) : function.apply(t), Function.identity()));
    }

    public static <R, T> Map<R, List<T>> groupMap(Collection<T> collection, SFunction<T, R> function) {
        return collection.stream().collect(Collectors.groupingBy(t -> function.isMethodReference() ? (R) new BeanWrapperImpl(t).getPropertyValue(function.getPropertyName()) : function.apply(t)));
    }
}

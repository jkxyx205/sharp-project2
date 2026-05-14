package com.rick.db.util;

import com.rick.common.function.SFunction;
import com.rick.common.util.CollectionOps;
import com.rick.db.repository.model.EntityId;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Rick.Xu
 * @date 2023/7/29 16:45
 * 可用 DataAccessUtils 替代
 */
@UtilityClass
@Deprecated
public class OperatorUtils {

    @Deprecated
    public static <E> Optional<E> expectedAsOptional(List<E> list) {
        return CollectionOps.expectedAsOptional(list);
    }

    public static <ID, T extends EntityId<ID>> Map<ID, T> map(List<T> list) {
         return list.stream().collect(Collectors.toMap(EntityId::getId, Function.identity()));
//        return map(list, EntityId::getId);
    }

    @Deprecated
    public static <R, T> Map<R, T> map(List<T> list, SFunction<T, R> function) {
        return CollectionOps.map(list, function);
    }

    public static <ID, T extends EntityId<ID>> Map<ID, List<T>> groupMap(List<T> list) {
          return list.stream().collect(Collectors.groupingBy(EntityId::getId));
//        return groupMap(list, EntityId::getId);
    }

    @Deprecated
    public static <R, T> Map<R, List<T>> groupMap(List<T> list, SFunction<T, R> function) {
        return CollectionOps.groupMap(list, function);
    }
}

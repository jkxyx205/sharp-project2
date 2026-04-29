package com.rick.db.repository;

import com.rick.common.function.SFunction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.*;

/**
 * @author Rick.Xu
 * @date 2025/8/20 14:00
 */
public interface EntityCodeDAO<T, ID> extends EntityDAO<T, ID> {

    /**
     * 获取 code 字段数据
     * @param code
     * @param propertyName
     * @param clazz
     * @return
     * @param <S>
     */
    <S> Optional<S> selectByCode(@NotBlank String code, @NotBlank String propertyName, Class<S> clazz);

    <S> List<S> selectByCodes(@NotEmpty Collection<String> codes, @NotBlank String propertyName, Class<S> clazz);

    Optional<T> selectByCode(@NotBlank String code);

    List<T> selectByCodes(@NotEmpty Collection<String> codes);

    Optional<ID> selectIdByCode(@NotBlank String code);

    List<ID> selectIdsByCodes(@NotEmpty Collection<String> codes);

    Map<String, ID> selectCodeIdMap(@NotEmpty Collection<String> codes);

    Optional<T> selectByCodeWithoutCascade(@NotBlank String code);

    <S> Map<String, S> getPropertyByCodes(@NotEmpty Set<String> codes, SFunction<T, S> function);

    <S> S getPropertyByCode(@NotBlank String code, SFunction<T, S> function);

}

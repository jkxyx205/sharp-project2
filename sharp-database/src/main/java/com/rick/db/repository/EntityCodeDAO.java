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

    Optional<T> selectByCode(@NotBlank String code);

    List<T> selectByCodes(@NotEmpty Collection<String> codes);

    /**
     * 根据 code & 字段 获取值
     * @param code
     * @param columnName
     * @param clazz
     * @return
     * @param <S>
     */
    <S> Optional<S> selectByCode(@NotBlank String code, @NotBlank String columnName, Class<S> clazz);

    <S> Map<String, S> selectByCodes(@NotEmpty Collection<String> codes, @NotBlank String columnName, Class<S> clazz);

    /**
     * 根据 code & 属性 获取值
     * @param code
     * @param function
     * @return
     * @param <S>
     */
    <S> Optional<S> selectByCode(@NotBlank String code, SFunction<T, S> function);

    <S> Map<String, S> selectByCodes(@NotEmpty Set<String> codes, SFunction<T, S> function);

    Optional<ID> selectIdByCode(@NotBlank String code);

    List<ID> selectIdsByCodes(@NotEmpty Collection<String> codes);

    Map<String, ID> selectCodeIdMap(@NotEmpty Collection<String> codes);

    Optional<T> selectByCodeWithoutCascade(@NotBlank String code);
}

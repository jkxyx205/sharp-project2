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

    Optional<ID> selectIdByCode(@NotBlank String code);

    List<ID> selectIdsByCodes(@NotEmpty Collection<String> codes);

    Map<String, ID> selectCodeIdMap(@NotEmpty Collection<String> codes);

    Optional<T> selectByCodeWithoutCascade(String code);

    <S> Map<String, S> getPropertyByCodes(Set<String> codes, SFunction<T, S> function);

    <S> S getPropertyByCode(String code, SFunction<T, S> function);

}

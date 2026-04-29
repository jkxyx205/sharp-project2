package com.rick.db.plugin;

import com.rick.common.function.SFunction;
import com.rick.db.repository.EntityCodeDAO;
import com.rick.db.repository.model.EntityIdCode;

import java.util.*;

/**
 * @author Rick.Xu
 * @date 2025/8/15 19:21
 */
public class BaseCodeServiceImpl<D extends EntityCodeDAO<T, ID>, T extends EntityIdCode<ID>, ID>
        extends BaseServiceImpl<D, T, ID>
        implements EntityCodeDAO<T, ID> {

    public BaseCodeServiceImpl(D baseDAO) {
        super(baseDAO);
    }

    @Override
    public <S> Optional<S> selectByCode(String code, String propertyName, Class<S> clazz) {
        return baseDAO.selectByCode(code, propertyName, clazz);
    }

    @Override
    public <S> List<S> selectByCodes(Collection<String> codes, String propertyName, Class<S> clazz) {
        return baseDAO.selectByCodes(codes, propertyName, clazz);
    }

    @Override
    public Optional<T> selectByCode(String code) {
        return baseDAO.selectByCode(code);
    }

    @Override
    public List<T> selectByCodes(Collection<String> codes) {
        return baseDAO.selectByCodes(codes);
    }

    @Override
    public Optional<ID> selectIdByCode(String code) {
        return baseDAO.selectIdByCode(code);
    }

    @Override
    public List<ID> selectIdsByCodes(Collection<String> codes) {
        return baseDAO.selectIdsByCodes(codes);
    }

    @Override
    public Map<String, ID> selectCodeIdMap(Collection<String> codes) {
        return baseDAO.selectCodeIdMap(codes);
    }

    @Override
    public Optional<T> selectByCodeWithoutCascade(String code) {
        return baseDAO.selectByCodeWithoutCascade(code);
    }

    @Override
    public <S> Map<String, S> getPropertyByCodes(Set<String> codes, SFunction<T, S> function) {
        return baseDAO.getPropertyByCodes(codes, function);
    }

    @Override
    public <S> S getPropertyByCode(String code, SFunction<T, S> function) {
        return baseDAO.getPropertyByCode(code, function);
    }
}

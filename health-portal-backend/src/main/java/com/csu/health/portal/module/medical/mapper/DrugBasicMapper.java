package com.csu.health.portal.module.medical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.medical.entity.DrugBasic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DrugBasicMapper extends BaseMapper<DrugBasic> {

    @Select("SELECT id, generic_name, brand_name, category, dosage_form, prescription_type " +
            "FROM drug_basic WHERE MATCH(generic_name, brand_name) AGAINST(CONCAT(#{keyword}, '*') IN BOOLEAN MODE) " +
            "LIMIT #{size}")
    List<DrugBasic> searchByFulltext(@Param("keyword") String keyword, @Param("size") int size);

    @Select("SELECT id, generic_name, brand_name, category, dosage_form, prescription_type " +
            "FROM drug_basic WHERE generic_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR brand_name LIKE CONCAT('%', #{keyword}, '%') " +
            "LIMIT #{size}")
    List<DrugBasic> searchByLike(@Param("keyword") String keyword, @Param("size") int size);

    @Select("SELECT category AS name, COUNT(*) AS value FROM drug_basic GROUP BY category")
    List<java.util.Map<String, Object>> countByCategory();

    @Select("SELECT dosage_form AS name, COUNT(*) AS value FROM drug_basic GROUP BY dosage_form")
    List<java.util.Map<String, Object>> countByDosageForm();
}

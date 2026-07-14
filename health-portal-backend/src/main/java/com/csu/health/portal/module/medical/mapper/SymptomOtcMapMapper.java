package com.csu.health.portal.module.medical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.medical.entity.SymptomOtcMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SymptomOtcMapMapper extends BaseMapper<SymptomOtcMap> {

    @Select("SELECT drug_id FROM symptom_otc_map WHERE symptom LIKE CONCAT('%', #{query}, '%') " +
            "OR #{query} LIKE CONCAT('%', symptom, '%') " +
            "LIMIT 50")
    List<Long> findDrugIdsBySymptom(@Param("query") String query);
}

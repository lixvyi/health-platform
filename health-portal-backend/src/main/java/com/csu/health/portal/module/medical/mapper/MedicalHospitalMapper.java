package com.csu.health.portal.module.medical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.medical.entity.MedicalHospital;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MedicalHospitalMapper extends BaseMapper<MedicalHospital> {

    @Select("SELECT DISTINCT province FROM medical_hospital ORDER BY province")
    List<String> selectDistinctProvinces();

    @Select("SELECT DISTINCT city FROM medical_hospital WHERE province = #{province} ORDER BY city")
    List<String> selectCitiesByProvince(@Param("province") String province);
}

package com.csu.health.portal.module.medical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.medical.entity.MedicalVaccineInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 疫苗信息Mapper
 */
@Mapper
public interface MedicalVaccineInfoMapper extends BaseMapper<MedicalVaccineInfo> {
}

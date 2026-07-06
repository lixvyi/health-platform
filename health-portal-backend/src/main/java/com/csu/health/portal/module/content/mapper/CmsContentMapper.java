package com.csu.health.portal.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.content.entity.CmsContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CmsContentMapper extends BaseMapper<CmsContent> {

    CmsContent selectByIdXml(Long id);

    List<CmsContent> selectPageByCondition(@Param("categoryCode") String categoryCode,
                                           @Param("keyword") String keyword,
                                           @Param("status") Integer status,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);

    long countByCondition(@Param("categoryCode") String categoryCode,
                          @Param("keyword") String keyword,
                          @Param("status") Integer status);

    int insertXml(CmsContent content);

    int updateDynamic(CmsContent content);

    int deleteByIdXml(Long id);

    int incrementViewCount(Long id);

    List<CmsContent> selectByIds(List<Long> ids);

    List<CmsContent> selectRecentPublished(@Param("categoryCode") String categoryCode,
                                           @Param("days") int days,
                                           @Param("limit") int limit);
}

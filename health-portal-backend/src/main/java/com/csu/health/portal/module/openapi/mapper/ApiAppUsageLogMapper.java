package com.csu.health.portal.module.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.csu.health.portal.module.openapi.entity.ApiAppUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ApiAppUsageLogMapper extends BaseMapper<ApiAppUsageLog> {

    @Select("SELECT COUNT(*) FROM api_app_usage_log " +
            "WHERE app_key = #{appKey} AND request_at >= #{since}")
    long countSince(@Param("appKey") String appKey, @Param("since") String since);

    /**
     * 按日统计指定 AppKey 的调用量（最近 N 天）
     */
    @Select("SELECT DATE(request_at) AS day, COUNT(*) AS count " +
            "FROM api_app_usage_log " +
            "WHERE app_key = #{appKey} AND request_at >= #{since} " +
            "GROUP BY DATE(request_at) ORDER BY day ASC")
    List<Map<String, Object>> dailyUsage(@Param("appKey") String appKey, @Param("since") String since);

    /**
     * 按日统计所有 AppKey 的调用量（最近 N 天）
     */
    @Select("SELECT al.app_key AS appKey, aa.app_name AS appName, " +
            "DATE(al.request_at) AS day, COUNT(*) AS count " +
            "FROM api_app_usage_log al " +
            "LEFT JOIN api_app aa ON aa.app_key = al.app_key " +
            "WHERE al.request_at >= #{since} " +
            "GROUP BY al.app_key, DATE(al.request_at) ORDER BY day ASC")
    List<Map<String, Object>> allDailyUsage(@Param("since") String since);

    /**
     * 今日各 AppKey 实时调用量
     */
    @Select("SELECT al.app_key AS appKey, aa.app_name AS appName, COUNT(*) AS count " +
            "FROM api_app_usage_log al " +
            "LEFT JOIN api_app aa ON aa.app_key = al.app_key " +
            "WHERE DATE(al.request_at) = CURDATE() " +
            "GROUP BY al.app_key ORDER BY count DESC")
    List<Map<String, Object>> todayUsage();

    /**
     * 总体统计
     */
    @Select("SELECT COUNT(DISTINCT app_key) AS appCount, COUNT(*) AS totalCalls " +
            "FROM api_app_usage_log WHERE request_at >= #{since}")
    Map<String, Object> overallStats(@Param("since") String since);
}

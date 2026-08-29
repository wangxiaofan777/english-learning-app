package com.lingo.app.admin.mapper;

import org.apache.ibatis.annotations.Select;

/** 管理后台仪表盘用的统计查询（简单聚合，不引入通用 SQL 层） */
public interface AdminStatsMapper {

  @Select("SELECT COUNT(DISTINCT user_id) FROM t_study_log WHERE study_date = #{date}")
  long todayActiveUsers(String date);

  @Select("SELECT COALESCE(SUM(minutes), 0) FROM t_study_log WHERE study_date = #{date}")
  long todayMinutes(String date);
}

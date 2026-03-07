package com.app.dao;

import com.app.entity.TaskDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskDAO extends BaseMapper<TaskDO> {
}

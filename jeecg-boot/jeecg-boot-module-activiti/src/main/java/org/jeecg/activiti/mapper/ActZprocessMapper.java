package org.jeecg.activiti.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.jeecg.activiti.entity.ActZprocess;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Description: 流程定义扩展表
 * @Author: pmc
 * @Date:   2020-03-22
 * @Version: V1.0
 */
public interface ActZprocessMapper extends BaseMapper<ActZprocess> {

    List<ActZprocess> selectNewestProcess(@Param("processKey") String processKey);
}

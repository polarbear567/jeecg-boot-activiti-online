package org.jeecg.activiti.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @author Leo Li
 * @date 2021-04-21 13:46
 */
@Data
@TableName("act_z_form_component")
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value="act_z_form_component对象", description="表单组件表")
public class ActFormComponent {
    @TableId(type = IdType.ID_WORKER_STR)
    @ApiModelProperty(value = "主键")
    private String id;
    @ApiModelProperty(value = "表单组件名称")
    private String text;
    @ApiModelProperty(value = "表单组件路由")
    private String routeName;
    @ApiModelProperty(value = "表单组件路径")
    private String component;
    @ApiModelProperty(value = "业务表名称")
    private String businessTable;
    @ApiModelProperty(value = "业务表类型")
    private String tableType;
    @ApiModelProperty(value = "其他信息")
    private String otherInfo;
}

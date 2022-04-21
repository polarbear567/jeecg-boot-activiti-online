package org.jeecg.singletabledemo.controller;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jeecg.activiti.entity.ActBusiness;
import org.jeecg.activiti.entity.ActZprocess;
import org.jeecg.activiti.service.impl.ActBusinessServiceImpl;
import org.jeecg.activiti.service.impl.ActZprocessServiceImpl;
import org.jeecg.activiti.util.ActHelpUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.UUIDGenerator;
import org.jeecg.singletabledemo.entity.Approval;
import org.jeecg.singletabledemo.service.IApprovalService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.aspect.annotation.AutoLog;

 /**
 * @Description: 审批表
 * @Author: jeecg-boot
 * @Date:   2022-04-12
 * @Version: V1.0
 */
@Api(tags="审批表")
@RestController
@RequestMapping("/singletabledemo/approval")
@Slf4j
public class ApprovalController extends JeecgController<Approval, IApprovalService> {
	@Autowired
	private IApprovalService approvalService;
	@Autowired
    private ActZprocessServiceImpl actZprocessService;
    @Autowired
    private ActBusinessServiceImpl actBusinessService;
    @Autowired
    private ActHelpUtil actHelpUtil;

	/**
	 * 分页列表查询
	 *
	 * @param approval
	 * @param pageNo
	 * @param pageSize
	 * @param req
	 * @return
	 */
	@AutoLog(value = "审批表-分页列表查询")
	@ApiOperation(value="审批表-分页列表查询", notes="审批表-分页列表查询")
	@GetMapping(value = "/list")
	public Result<?> queryPageList(Approval approval,
								   @RequestParam(name="pageNo", defaultValue="1") Integer pageNo,
								   @RequestParam(name="pageSize", defaultValue="10") Integer pageSize,
								   HttpServletRequest req) {
		QueryWrapper<Approval> queryWrapper = QueryGenerator.initQueryWrapper(approval, req.getParameterMap());
		Page<Approval> page = new Page<Approval>(pageNo, pageSize);
		IPage<Approval> pageList = approvalService.page(page, queryWrapper);
		return Result.OK(pageList);
	}
	
	/**
	 *   添加
	 *
	 * @param approval
	 * @return
	 */
	@AutoLog(value = "审批表-添加")
	@ApiOperation(value="审批表-添加", notes="审批表-添加")
	@PostMapping(value = "/add")
	public Result<?> add(@RequestBody Approval approval) {
		approvalService.save(approval);
        ActZprocess actZprocess = actZprocessService.getActZprocessByTableName("approval");
        if (actZprocess == null) {
            return Result.OK("添加成功！但流程未定义，若需操作流程，请前往工作流中定义对应流程！");
        }
        ActBusiness actBusiness = new ActBusiness();
        LoginUser sysUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        String username = sysUser.getUsername();
        actBusiness.setId(UUIDGenerator.generate());
        actBusiness.setUserId(username);
        actBusiness.setTableId(approval.getId());
        actBusiness.setProcDefId(actZprocess.getId());
        actBusiness.setTitle(actZprocess.getName());
        actBusiness.setTableName(actZprocess.getBusinessTable());
        actBusinessService.save(actBusiness);
		return Result.OK("添加成功！");
	}
	
	/**
	 *  编辑
	 *
	 * @param approval
	 * @return
	 */
	@AutoLog(value = "审批表-编辑")
	@ApiOperation(value="审批表-编辑", notes="审批表-编辑")
	@PutMapping(value = "/edit")
	public Result<?> edit(@RequestBody Approval approval) {
		approvalService.updateById(approval);
		return Result.OK("编辑成功!");
	}
	
	/**
	 *   通过id删除
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "审批表-通过id删除")
	@ApiOperation(value="审批表-通过id删除", notes="审批表-通过id删除")
	@DeleteMapping(value = "/delete")
	public Result<?> delete(@RequestParam(name="id",required=true) String id) {
	    if (!actHelpUtil.deleteBusiness("approval", id)) {
            return Result.error("删除失败,该记录有对应的流程没有完成,请完成流程后删除,或将流程撤回草稿状态后删除");
        }
		approvalService.removeById(id);
		return Result.OK("删除成功!");
	}
	
	/**
	 *  批量删除
	 *
	 * @param ids
	 * @return
	 */
	@AutoLog(value = "审批表-批量删除")
	@ApiOperation(value="审批表-批量删除", notes="审批表-批量删除")
	@DeleteMapping(value = "/deleteBatch")
	public Result<?> deleteBatch(@RequestParam(name="ids",required=true) String ids) {
	    List<String> delFailIds = new ArrayList<>();
        List<String> canDelIds = new ArrayList<>();
        for (String id : StringUtils.split(ids, ",")) {
            if (!actHelpUtil.deleteBusiness("approval", id)) {
                delFailIds.add(id);
                continue;
            }
            canDelIds.add(id);
        }
        this.approvalService.removeByIds(canDelIds);
        return delFailIds.isEmpty() ? Result.OK("批量删除成功!") : Result.OK("部分记录由于工单原因导致删除失败,请检查后再删除!", delFailIds);
	}
	
	/**
	 * 通过id查询
	 *
	 * @param id
	 * @return
	 */
	@AutoLog(value = "审批表-通过id查询")
	@ApiOperation(value="审批表-通过id查询", notes="审批表-通过id查询")
	@GetMapping(value = "/queryById")
	public Result<?> queryById(@RequestParam(name="id",required=true) String id) {
		Approval approval = approvalService.getById(id);
		if(approval==null) {
			return Result.error("未找到对应数据");
		}
		return Result.OK(approval);
	}

    /**
    * 导出excel
    *
    * @param request
    * @param approval
    */
    @RequestMapping(value = "/exportXls")
    public ModelAndView exportXls(HttpServletRequest request, Approval approval) {
        return super.exportXls(request, approval, Approval.class, "审批表");
    }

    /**
      * 通过excel导入数据
    *
    * @param request
    * @param response
    * @return
    */
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, Approval.class);
    }

}

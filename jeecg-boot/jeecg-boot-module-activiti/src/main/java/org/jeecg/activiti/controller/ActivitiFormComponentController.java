package org.jeecg.activiti.controller;

import org.jeecg.activiti.entity.ActFormComponent;
import org.jeecg.activiti.service.impl.ActFormComponentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Leo Li
 * @date 2021-04-21 16:03
 */
@RestController
@RequestMapping("/activiti/formComponent")
@Slf4j
public class ActivitiFormComponentController {

    @Autowired
    private ActFormComponentServiceImpl actFormComponentService;

    @RequestMapping("/query")
    @ResponseBody
    public Result query(HttpServletRequest request){
        Map<String, Object> map = new HashMap<>();
        map.put("name", request.getParameter("keyWord"));
        List<ActFormComponent> models = actFormComponentService.listByMap(map);
        return Result.OK(models);
    }

    @RequestMapping("/list")
    @ResponseBody
    public Result list() {
        List<ActFormComponent> list = actFormComponentService.list();
        return Result.OK(list);
    }

    @PostMapping(value = "/add")
    public Result add(@RequestBody ActFormComponent actFormComponent) {
        actFormComponentService.save(actFormComponent);
        return Result.OK("添加成功！");
    }

    @PutMapping(value = "/edit")
    public Result<?> edit(@RequestBody ActFormComponent actFormComponent) {
        actFormComponentService.updateById(actFormComponent);
        return Result.OK("编辑成功!");
    }

    @DeleteMapping(value = "/delete")
    public Result<?> delete(@RequestParam(name="id",required=true) String id) {
        actFormComponentService.removeById(id);
        return Result.OK("删除成功!");
    }
}

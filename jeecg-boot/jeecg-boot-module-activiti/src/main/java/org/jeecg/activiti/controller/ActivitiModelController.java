package org.jeecg.activiti.controller;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jeecg.activiti.entity.ActZprocess;
import org.jeecg.activiti.service.impl.ActZprocessServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ModelQuery;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.image.ProcessDiagramGenerator;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/activiti/models")
@Slf4j
public class ActivitiModelController {
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;
    @Autowired
    private TaskService taskService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ActZprocessServiceImpl actZprocessService;

    @RequestMapping("/modelListData")
    @ResponseBody
    public Result modelListData( HttpServletRequest request){
        log.info("-------------????????????-------------");
        ModelQuery modelQuery = repositoryService.createModelQuery();
        String keyWord = request.getParameter("keyWord");//???????????????
        if (StrUtil.isNotBlank(keyWord)){
            modelQuery.modelNameLike("%"+keyWord+"%");
        }
        List<Model> models = modelQuery.orderByCreateTime().desc().list();

        return Result.ok(models);
    }

    @RequestMapping("/create")
    public void newModel(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
       try {
           //????????????????????????
           Model model = repositoryService.newModel();
           //????????????????????????
           int revision = 1;
           String name = request.getParameter("name");
           String description = request.getParameter("description");
           String key = request.getParameter("key");
           if (StrUtil.isBlank(name)){
               name = "new-process";
           }
           if (StrUtil.isBlank(description)){
               description = "description";
           }
           if (StrUtil.isBlank(key)){
               key = "processKey";
           }


           ObjectNode modelNode = objectMapper.createObjectNode();
           modelNode.put(ModelDataJsonConstants.MODEL_NAME, name);
           modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, description);
           modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);

           model.setName(name);
           model.setKey(key);
           model.setMetaInfo(modelNode.toString());

           repositoryService.saveModel(model);
           String id = model.getId();

           //??????ModelEditorSource
           ObjectNode editorNode = objectMapper.createObjectNode();
           editorNode.put("id", "canvas");
           editorNode.put("resourceId", "canvas");
           ObjectNode stencilSetNode = objectMapper.createObjectNode();
           stencilSetNode.put("namespace",
                   "http://b3mn.org/stencilset/bpmn2.0#");
           editorNode.put("stencilset", stencilSetNode);
           repositoryService.addModelEditorSource(id, editorNode.toString().getBytes("utf-8"));
           response.sendRedirect(request.getContextPath() + "/activiti/modeler.html?modelId=" + id);
       }catch (IOException e){
           e.printStackTrace();
           log.info("?????????????????????");
       }

    }

    @RequestMapping("/delete/{id}")
    public @ResponseBody Result deleteModel(@PathVariable("id")String id){
        repositoryService.deleteModel(id);
        return Result.ok("???????????????");
    }


    @RequestMapping("/deployment/{id}")
    public @ResponseBody Result deploy(@PathVariable("id")String id) {

        // ????????????
        Model modelData = repositoryService.getModel(id);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());

        if (bytes == null) {
            return Result.error("??????????????????????????????????????????????????????");
        }

        try {
            JsonNode modelNode = new ObjectMapper().readTree(bytes);

            BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
            if(model.getProcesses().size()==0){
                return Result.error("??????????????????????????????????????????????????????");
            }
            byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

            // ????????????????????????
            String processName = modelData.getName() + ".bpmn20.xml";
            Deployment deployment = repositoryService.createDeployment()
                    .name(modelData.getName())
                    .addString(processName, new String(bpmnBytes, "UTF-8"))
                    .deploy();
            String metaInfo = modelData.getMetaInfo();
            JSONObject metaInfoMap = JSON.parseObject(metaInfo);
            // ?????????????????? ??????????????????????????????
            List<ProcessDefinition> list = repositoryService.createProcessDefinitionQuery().deploymentId(deployment.getId()).list();
            for (ProcessDefinition pd : list) {
                ActZprocess actZprocess = new ActZprocess();
                actZprocess.setId(pd.getId());
                actZprocess.setName(modelData.getName());
                actZprocess.setProcessKey(modelData.getKey());
                actZprocess.setDeploymentId(deployment.getId());
                actZprocess.setDescription(metaInfoMap.getString(ModelDataJsonConstants.MODEL_DESCRIPTION));
                actZprocess.setVersion(pd.getVersion());
                actZprocess.setDiagramName(pd.getDiagramResourceName());
                actZprocessService.setAllOldByProcessKey(modelData.getKey());
                actZprocess.setLatest(true);
                actZprocessService.save(actZprocess);
            }
        }catch (Exception e){
            String err = e.toString();
            log.error(e.getMessage(),e);
            if (err.indexOf("NCName")>-1){
                return Result.error("??????????????????????????????????????????????????????????????????????????????????????????????????????");
            }
            if (err.indexOf("PRIMARY")>-1){
                return Result.error("????????????????????????????????????key?????????");
            }
            return Result.error("???????????????");
        }

        return Result.ok("????????????");
    }
    /*???????????????????????????*/
    @RequestMapping(value = "/getHighlightImg/{id}", method = RequestMethod.GET)
    public void getHighlightImg(@PathVariable String id, HttpServletResponse response){
        InputStream inputStream = null;
        ProcessInstance pi = null;
        String picName = "";
        // ????????????
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery().processInstanceId(id).singleResult();
        if (hpi.getEndTime() != null) {
            // ??????????????????????????????
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().processDefinitionId(hpi.getProcessDefinitionId()).singleResult();
            picName = pd.getDiagramResourceName();
            inputStream = repositoryService.getResourceAsStream(pd.getDeploymentId(), pd.getDiagramResourceName());
        } else {
            pi = runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult();
            BpmnModel bpmnModel = repositoryService.getBpmnModel(pi.getProcessDefinitionId());

            List<String> highLightedActivities = new ArrayList<String>();
            // ??????????????????
            List<Task> tasks = taskService.createTaskQuery().processInstanceId(id).list();
            for (Task task : tasks) {
                highLightedActivities.add(task.getTaskDefinitionKey());
            }

            List<String> highLightedFlows = new ArrayList<String>();
            ProcessDiagramGenerator diagramGenerator = processEngineConfiguration.getProcessDiagramGenerator();
            //"??????"
            inputStream = diagramGenerator.generateDiagram(bpmnModel, "png", highLightedActivities, highLightedFlows,
                    "??????", "??????", "??????",null, 1.0);
            picName = pi.getName()+".png";
        }
        try {
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(picName, "UTF-8"));
            byte[] b = new byte[1024];
            int len = -1;
            while ((len = inputStream.read(b, 0, 1024)) != -1) {
                response.getOutputStream().write(b, 0, len);
            }
            response.flushBuffer();
        } catch (IOException e) {
            log.error(e.toString());
            throw new JeecgBootException("????????????????????????");
        }
    }
    /**????????????????????????*/
    @RequestMapping(value = "/export", method = RequestMethod.GET)
    public void exportResource(@RequestParam String id, HttpServletResponse response){

        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(id).singleResult();

        String resourceName = pd.getDiagramResourceName();
        InputStream inputStream = repositoryService.getResourceAsStream(pd.getDeploymentId(),
                resourceName);

        try {
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(resourceName, "UTF-8"));
            byte[] b = new byte[1024];
            int len = -1;
            while ((len = inputStream.read(b, 0, 1024)) != -1) {
                response.getOutputStream().write(b, 0, len);
            }
            response.flushBuffer();
        } catch (IOException e) {
            log.error(e.toString());
        }
    }

}

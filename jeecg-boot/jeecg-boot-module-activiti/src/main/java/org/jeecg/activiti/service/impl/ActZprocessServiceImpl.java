package org.jeecg.activiti.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Maps;
import org.jeecg.activiti.entity.ActBusiness;
import org.jeecg.activiti.entity.ActNode;
import org.jeecg.activiti.entity.ActZprocess;
import org.jeecg.activiti.entity.ActivitiConstant;
import org.jeecg.activiti.entity.Department;
import org.jeecg.activiti.entity.ProcessNodeVo;
import org.jeecg.activiti.entity.Role;
import org.jeecg.activiti.mapper.ActZprocessMapper;
import org.jeecg.activiti.service.IActZprocessService;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.*;
import org.activiti.engine.IdentityService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmActivity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.task.TaskDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.dto.message.BusMessageDTO;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.DateUtils;
import org.jeecg.common.util.SpringContextUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Description: ?????????????????????
 * @Author: pmc
 * @Author: Leo Li
 * @Date:   2020-03-22
 * @Version: V1.0
 */
@Service
public class ActZprocessServiceImpl extends ServiceImpl<ActZprocessMapper, ActZprocess> implements IActZprocessService {
    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TaskService taskService;
    @Autowired
    private ActNodeServiceImpl actNodeService;
    @Autowired
    private ActBusinessServiceImpl actBusinessService;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private ISysBaseAPI sysBaseAPI;

    /**
     * ??????key????????????????????????
     * @param processKey
     */
    public void setAllOldByProcessKey(String processKey) {
        List<ActZprocess> list = this.list(new LambdaQueryWrapper<ActZprocess>().eq(ActZprocess::getProcessKey,processKey));
        if(list==null||list.size()==0){
            return;
        }
        list.forEach(item -> {
            item.setLatest(false);
        });
        this.updateBatchById(list);
    }

    /**
     * ???????????????????????????
     * @param processKey
     */
    public void setLatestByProcessKey(String processKey) {
        ActZprocess actProcess = this.findTopByProcessKeyOrderByVersionDesc(processKey);
        if(actProcess==null){
            return;
        }
        actProcess.setLatest(true);
        this.updateById(actProcess);
    }

    private ActZprocess findTopByProcessKeyOrderByVersionDesc(String processKey) {
        List<ActZprocess> list = this.list(new LambdaQueryWrapper<ActZprocess>().eq(ActZprocess::getProcessKey, processKey)
                .orderByDesc(ActZprocess::getVersion)
        );
        if (CollUtil.isNotEmpty(list)){
            return list.get(0);
        }
        return null;
    }

    public String startProcess(ActBusiness actBusiness) {
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        // ??????????????????
        identityService.setAuthenticatedUserId(loginUser.getUsername());
        // ???????????? ??????????????????id??????
        Map<String, Object> params = actBusiness.getParams();
        params.put("tableId", actBusiness.getTableId());
        ActBusiness act = actBusinessService.getById(actBusiness.getId());
        String tableName = act.getTableName();
        String tableId = act.getTableId();
        if (StrUtil.isBlank(tableId)||StrUtil.isBlank(tableName)){
            throw new JeecgBootException("????????????????????????");
        }
        /*??????????????????*/
        Map<String, Object> busiData = actBusinessService.getBusiData(tableId, tableName);
        for (String key : busiData.keySet()) {
            params.put(key,busiData.get(key));
        }
        ProcessInstance pi = runtimeService.startProcessInstanceById(actBusiness.getProcDefId(), actBusiness.getId(), params);
        // ????????????????????????
        runtimeService.setProcessInstanceName(pi.getId(), actBusiness.getTitle());
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(pi.getId()).list();
        for(Task task : tasks){
            if(actBusiness.getFirstGateway()){
                // ????????????
                List<LoginUser> users = getNode(task.getTaskDefinitionKey(),tableName,tableId).getUsers();
                // ?????????????????????????????????????????? ??????????????????
                if(users==null||users.size()==0){
                    throw new RuntimeException("???????????????????????????????????????????????????????????????");
                }else{
                    // ???????????????????????????????????????
                    for(LoginUser user : users){
                        taskService.addCandidateUser(task.getId(), user.getUsername());
                        // ???????????????
                        sendActMessage(loginUser,user,actBusiness,task.getName(), actBusiness.getSendMessage(),
                                actBusiness.getSendSms(), actBusiness.getSendEmail());
                    }
                }
            }else {
                // ???????????????????????????
                String assignees = actBusiness.getAssignees();
                for (String assignee : assignees.split(",")) {
                    taskService.addCandidateUser(task.getId(), assignee);
                    // ???????????????
                    LoginUser user = sysBaseAPI.getUserByName(assignee);
                    sendActMessage(loginUser,user,actBusiness,task.getName(), actBusiness.getSendMessage(),
                            actBusiness.getSendSms(), actBusiness.getSendEmail());
                }
            }
            // ?????????????????????
            taskService.setPriority(task.getId(), actBusiness.getPriority());
        }
        return pi.getId();
    }

    /**
     * ??????????????????
     * @param fromUser ?????????
     * @param toUser ?????????
     * @param act ??????
     * @param taskName
     * @param sendMessage ????????????
     * @param sendSms ????????????
     * @param sendEmail ????????????
     */
    public void sendActMessage(LoginUser fromUser, LoginUser toUser, ActBusiness act, String taskName, Boolean sendMessage, Boolean sendSms, Boolean sendEmail) {
        String title = String.format("?????????????????????????????????"+act.getTitle());
        Map<String, String> msgMap = Maps.newHashMap();
                        /*???????????????  ${bpm_name}
???????????????  ${bpm_task}
???????????? :    ${datetime}
???????????? :    ${remark}*/
        msgMap.put("bpm_name",act.getTitle());
        msgMap.put("bpm_task",taskName);
        msgMap.put("datetime", DateUtils.now());
        msgMap.put("remark", "????????????????????????????????????");
        /*??????????????????*/
        //String msgText = sysBaseAPI.parseTemplateByCode("bpm_cuiban", msgMap);
        String msgText = String.format("?????????????????????????????????"+act.getTitle());
        this.sendMessage(act.getId(),fromUser,toUser,title,msgText,sendMessage,sendSms,sendEmail);
    }

    /**
     * ?????????
     * @param actBusId ????????????id
     * @param fromUser ?????????
     * @param toUser ?????????
     * @param title ??????
     * @param msgText ????????????
     * @param sendMessage ????????????
     * @param sendSms ??????
     * @param sendEmail ??????
     */
    public void sendMessage(String actBusId,LoginUser fromUser, LoginUser toUser,String title,String msgText,  Boolean sendMessage, Boolean sendSms, Boolean sendEmail) {
        if (sendMessage!=null&&sendMessage){
            BusMessageDTO messageDTO = new BusMessageDTO(fromUser.getUsername(),toUser.getUsername(),title,msgText,"2","bpm",actBusId);
            sysBaseAPI.sendBusAnnouncement(messageDTO);
        }
        //todo ???????????????????????????????????????????????????????????????
        if (sendSms!=null&&sendSms&& StrUtil.isNotBlank(toUser.getPhone())){
            //DySmsHelper.sendSms(toUser.getPhone(), obj, DySmsEnum.REGISTER_TEMPLATE_CODE)
        }
        if (sendEmail!=null&&sendEmail&& StrUtil.isNotBlank(toUser.getEmail())){
            JavaMailSender mailSender = (JavaMailSender) SpringContextUtils.getBean("mailSender");
            SimpleMailMessage message = new SimpleMailMessage();
// ???????????????????????????
//            message.setFrom(emailFrom);
            message.setTo(toUser.getEmail());
            //message.setSubject(es_title);
            message.setText(msgText);
            mailSender.send(message);
        }
    }

    public ProcessNodeVo getNode(String nodeId, String tableName, String tableId) {

        ProcessNodeVo node = new ProcessNodeVo();
        // ??????????????????
        List<LoginUser> users = getNodetUsers(nodeId,tableName,tableId);
        node.setUsers(removeDuplicate(users));
        return node;
    }
    /**
     * ?????????????????????
     * @param nodeId
     */
    public List<LoginUser> getNodetUsers(String nodeId,String tableName,String tableId){
        ActBusiness business = actBusinessService.getOne(new LambdaQueryWrapper<ActBusiness>().eq(ActBusiness::getTableId, tableId).eq(ActBusiness::getTableName, tableName));
        String procDefId = business.getProcDefId();
        // ??????????????????
        List<LoginUser> users = actNodeService.findUserByNodeId(nodeId,procDefId);
        // ??????????????????
        List<Role> roles = actNodeService.findRoleByNodeId(nodeId,procDefId);
        for(Role r : roles){
            List<LoginUser> userList = actNodeService.findUserByRoleId(r.getId());
            users.addAll(userList);
        }
        // ??????
        List<Department> departments = actNodeService.findDepartmentByNodeId(nodeId,procDefId);
        for (Department d : departments){
            List<LoginUser> userList = actNodeService.findUserDepartmentId(d.getId());
            users.addAll(userList);
        }
        // ???????????????
        List<Department> departmentManages = actNodeService.findDepartmentManageByNodeId(nodeId,procDefId);
        for (Department d : departmentManages){
            List<LoginUser> userList = actNodeService.findUserDepartmentManageId(d.getId());
            users.addAll(userList);
        }
        // ????????????????????????
        if(actNodeService.hasChooseDepHeader(nodeId,procDefId)){
            List<LoginUser> allUser = actNodeService.queryAllUser();
            //???????????????????????????
            String createBy = getCreateBy(tableName,tableId);
            List<String> departIds = sysBaseAPI.getDepartIdsByUsername(createBy);

            for (String departId : departIds) {
                List<LoginUser> collect = allUser.stream().filter(u -> u.getDepartIds() != null && u.getDepartIds().indexOf(departId) > -1).collect(Collectors.toList());
                users.addAll(collect);
            }
        }
        // ?????????
        if(actNodeService.hasChooseSponsor(nodeId,procDefId)){
            String createBy = getCreateBy(tableName,tableId);
            LoginUser userByName = sysBaseAPI.getUserByName(createBy);
            users.add(userByName);
        }
        // ????????????
        if(actNodeService.hasFormVariable(nodeId,procDefId)){
            List<String> variableNames = actNodeService.findFormVariableByNodeId(nodeId,procDefId);
            if(!variableNames.isEmpty()){
                Map<String, Object> applyForm = actBusinessService.getApplyForm(tableId, tableName);
                for(String variable : variableNames){
                    // ????????????
                    String type = "user";
                    String paramName = variable;

                    int i = variable.indexOf(":");
                    if(i>0){
                        type = variable.substring(i + 1);
                        paramName = variable.substring(0,i);
                    }
                    // ????????????????????????
                    String paramVal = (String)applyForm.get(paramName);

                    if(StringUtils.isNotEmpty(paramVal)){
                        for(String val : StringUtils.split(paramVal,',')) {
                            if(StringUtils.equalsIgnoreCase(type,"role")){
                                List<LoginUser> roleUsers = actNodeService.findUserByRoleId(val);
                                users.addAll(roleUsers);
                            }else if(StringUtils.equalsIgnoreCase(type,"user")){
                                LoginUser user = sysBaseAPI.getUserByName(val);
                                if(user!=null){
                                    users.add(user);
                                }
                            }else if(StringUtils.equalsIgnoreCase(type,"dept")){
                                List<LoginUser> depUsers = actNodeService.findUserDepartmentId(val);
                                users.addAll(depUsers);
                            }else if(StringUtils.equalsIgnoreCase(type,"deptManage")){
                                List<LoginUser> depManageUsers = actNodeService.findUserDepartmentManageId(val);
                                users.addAll(depManageUsers);
                            }
                        }

                    }


                }
            }
        }

        // ?????????????????????
        users = users.stream().filter(u->StrUtil.equals("0",u.getDelFlag()+"")).collect(Collectors.toList());
        return users;
    }

    /**
     * ????????????id???????????????
     * @param nodeId
     * @return
     */
    public String getUserByNodeid(String nodeId) {
        ActNode actNode = actNodeService.getOne(new LambdaQueryWrapper<ActNode>().eq(ActNode::getNodeId, nodeId).last("limit 1"));
        String procDefId = actNode.getProcDefId();
        ActBusiness actBusiness = actBusinessService.getOne(new LambdaQueryWrapper<ActBusiness>().eq(ActBusiness::getProcDefId, procDefId).last("limit 1"));
        return actBusiness.getCreateBy();
    }

    /**
     * ????????????????????????
     * @param tableName
     * @param tableId
     * @return
     */
    public String getCreateBy(String tableName,String tableId) {
        Map<String, Object> applyForm = actBusinessService.getApplyForm(tableId, tableName);
        return applyForm.get("createBy").toString();
    }

    /**
     * ??????
     * @param list
     * @return
     */
    private List<LoginUser> removeDuplicate(List<LoginUser> list) {

        LinkedHashSet<LoginUser> set = new LinkedHashSet<>(list.size());
        set.addAll(list);
        list.clear();
        list.addAll(set);
        entityManager.clear();
        list.forEach(u -> {
            u.setPassword(null);
        });
        return list;
    }

    public ProcessNodeVo getFirstNode(String procDefId,String tableName,String tableId) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(procDefId);

        ProcessNodeVo node = new ProcessNodeVo();

        List<Process> processes = bpmnModel.getProcesses();
        Collection<FlowElement> elements = processes.get(0).getFlowElements();
        // ??????????????????
        StartEvent startEvent = null;
        for (FlowElement element : elements) {
            if (element instanceof StartEvent) {
                startEvent = (StartEvent) element;
                break;
            }
        }
        FlowElement e = null;
        // ??????????????????????????????
        SequenceFlow sequenceFlow = startEvent.getOutgoingFlows().get(0);
        for (FlowElement element : elements) {
            if(element.getId().equals(sequenceFlow.getTargetRef())){
                if(element instanceof UserTask){
                    e = element;
                    node.setType(1);
                    break;
                }else if(element instanceof ExclusiveGateway){
                    e = element;
                    node.setType(3);
                    break;
                }else if(element instanceof ParallelGateway){
                    e = element;
                    node.setType(4);
                    break;
                }else{
                    throw new RuntimeException("?????????????????????????????????????????????????????????????????????????????????????????????");
                }
            }
        }
        // ?????????????????????????????????
        if(e instanceof ExclusiveGateway || e instanceof ParallelGateway){
            return node;
        }
        node.setTitle(e.getName());
        // ??????????????????
        List<LoginUser> users = getNodetUsers(e.getId(),tableName,tableId);
        node.setUsers(removeDuplicate(users));
        return node;
    }

    public ProcessNodeVo getNextNode(String procDefId, String currActId,String procInsId) {
        // ??????????????????id????????????????????????
        ActBusiness actBusiness = actBusinessService.getOne(new LambdaQueryWrapper<ActBusiness>().eq(ActBusiness::getProcInstId, procInsId));

        ProcessNodeVo node = new ProcessNodeVo();
        // ??????????????????id
        ProcessDefinitionEntity dfe = (ProcessDefinitionEntity) ((RepositoryServiceImpl)repositoryService).getDeployedProcessDefinition(procDefId);
        // ??????????????????
        List<ActivityImpl> activitiList = dfe.getActivities();
        // ???????????????????????????????????????????????????????????????????????????
        for(ActivityImpl activityImpl : activitiList){
            if (activityImpl.getId().equals(currActId)) {
                // ?????????????????????
                List<PvmTransition> pvmTransitions = activityImpl.getOutgoingTransitions();

                PvmActivity pvmActivity = pvmTransitions.get(0).getDestination();

                String type = pvmActivity.getProperty("type").toString();
                if("userTask".equals(type)){
                    // ??????????????????
                    node.setType(ActivitiConstant.NODE_TYPE_TASK);
                    node.setTitle(ObjectUtils.identityToString(pvmActivity.getProperty("name")));
                    // ??????????????????
                    List<LoginUser> users = getNodetUsers(pvmActivity.getId(),actBusiness.getTableName(),actBusiness.getTableId());
                    node.setUsers(removeDuplicate(users));
                }else if("exclusiveGateway".equals(type)){
                    // ????????????
                    node.setType(ActivitiConstant.NODE_TYPE_EG);
                    ActivityImpl pvmActivity1 = (ActivityImpl) pvmActivity;
                    /*????????????*/
                    Map<String, Object> vals = Maps.newHashMap();

                    LambdaQueryWrapper<ActBusiness> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ActBusiness::getProcInstId,procInsId);
                    ActBusiness one = actBusinessService.getOne(wrapper);
                    vals = actBusinessService.getApplyForm(one.getTableId(), one.getTableName());

                    TaskDefinition taskDefinition = actNodeService.nextTaskDefinition(pvmActivity1, pvmActivity1.getId(), vals, procInsId);
                    List<LoginUser> users = getNodetUsers(taskDefinition.getKey(),actBusiness.getTableName(),actBusiness.getTableId());
                    node.setUsers(removeDuplicate(users));
                }else if("parallelGateway".equals(type)){
                    // ????????????
                    node.setType(ActivitiConstant.NODE_TYPE_PG);
                }else if("endEvent".equals(type)){
                    // ??????
                    node.setType(ActivitiConstant.NODE_TYPE_END);
                }else{
                    throw new JeecgBootException("????????????????????????????????????????????????");
                }
                break;
            }
        }

        return node;
    }

    @Override
    public List<ActZprocess> queryNewestProcess(String processKey) {
        return baseMapper.selectNewestProcess(processKey);
    }

    public ActZprocess getActZprocessByTableName(String tableName) {
        Map<String, Object> map = new HashMap<>();
        map.put("business_table", tableName);
        return baseMapper.selectByMap(map).stream().findFirst().orElse(null);
    }
}

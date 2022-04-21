package org.jeecg.singletabledemo.service.impl;

import org.jeecg.singletabledemo.entity.Approval;
import org.jeecg.singletabledemo.mapper.ApprovalMapper;
import org.jeecg.singletabledemo.service.IApprovalService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 审批表
 * @Author: jeecg-boot
 * @Date:   2022-04-12
 * @Version: V1.0
 */
@Service
public class ApprovalServiceImpl extends ServiceImpl<ApprovalMapper, Approval> implements IApprovalService {

}

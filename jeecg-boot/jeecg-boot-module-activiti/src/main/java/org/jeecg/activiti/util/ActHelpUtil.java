package org.jeecg.activiti.util;

import org.jeecg.activiti.entity.ActBusiness;
import org.jeecg.activiti.entity.ActivitiConstant;
import org.jeecg.activiti.service.impl.ActBusinessServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Leo Li
 * @date 2021-04-19 14:04
 */
@Component
public class ActHelpUtil {

    @Autowired
    private ActBusinessServiceImpl actBusinessService;

    public boolean deleteBusiness(String tableName, String tableId) {
        ActBusiness actBusiness = actBusinessService.getActBusinessByTableInfo(tableName, tableId);
        if (actBusiness != null) {
            if (!actBusiness.getStatus().equals(ActivitiConstant.STATUS_TO_APPLY)) {
                return false;
            }
            actBusinessService.removeById(actBusiness.getId());
        }
        return true;
    }
}

<template>
  <a-card :bordered="false">
    <!-- 查询区域 -->
    <div class="table-page-search-wrapper">
      <a-form layout="inline" @keyup.enter.native="searchQuery">
        <a-row :gutter="24">
          <a-col :xl="6" :lg="7" :md="8" :sm="24">
            <a-form-item label="姓名">
              <a-input placeholder="请输入姓名" v-model="queryParam.name"></a-input>
            </a-form-item>
          </a-col>
          <a-col :xl="6" :lg="7" :md="8" :sm="24">
            <a-form-item label="部门">
              <j-select-depart placeholder="请选择部门" v-model="queryParam.dept"/>
            </a-form-item>
          </a-col>
          <template v-if="toggleSearchStatus">
            <a-col :xl="6" :lg="7" :md="8" :sm="24">
              <a-form-item label="老师">
                <j-select-user-by-dep placeholder="请选择老师" v-model="queryParam.teacher"/>
              </a-form-item>
            </a-col>
            <a-col :xl="6" :lg="7" :md="8" :sm="24">
              <a-form-item label="审核状态">
                <a-input placeholder="请输入审核状态" v-model="queryParam.actStatus"></a-input>
              </a-form-item>
            </a-col>
          </template>
          <a-col :xl="6" :lg="7" :md="8" :sm="24">
            <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
              <a-button type="primary" @click="searchQuery" icon="search">查询</a-button>
              <a-button type="primary" @click="searchReset" icon="reload" style="margin-left: 8px">重置</a-button>
              <a @click="handleToggleSearch" style="margin-left: 8px">
                {{ toggleSearchStatus ? '收起' : '展开' }}
                <a-icon :type="toggleSearchStatus ? 'up' : 'down'"/>
              </a>
            </span>
          </a-col>
        </a-row>
      </a-form>
    </div>
    <!-- 查询区域-END -->

    <!-- 操作按钮区域 -->
    <div class="table-operator">
      <a-button @click="handleAdd" type="primary" icon="plus">新增</a-button>
      <a-button type="primary" icon="download" @click="handleExportXls('审批表')">导出</a-button>
      <a-upload name="file" :showUploadList="false" :multiple="false" :headers="tokenHeader" :action="importExcelUrl" @change="handleImportExcel">
        <a-button type="primary" icon="import">导入</a-button>
      </a-upload>
      <!-- 高级查询区域 -->
      <j-super-query :fieldList="superFieldList" ref="superQueryModal" @handleSuperQuery="handleSuperQuery"></j-super-query>
      <a-dropdown v-if="selectedRowKeys.length > 0">
        <a-menu slot="overlay">
          <a-menu-item key="1" @click="batchDel"><a-icon type="delete"/>删除</a-menu-item>
        </a-menu>
        <a-button style="margin-left: 8px"> 批量操作 <a-icon type="down" /></a-button>
      </a-dropdown>
    </div>

    <!-- table区域-begin -->
    <div>
      <div class="ant-alert ant-alert-info" style="margin-bottom: 16px;">
        <i class="anticon anticon-info-circle ant-alert-icon"></i> 已选择 <a style="font-weight: 600">{{ selectedRowKeys.length }}</a>项
        <a style="margin-left: 24px" @click="onClearSelected">清空</a>
      </div>

      <a-table
        ref="table"
        size="middle"
        :scroll="{x:true}"
        bordered
        rowKey="id"
        :columns="columns"
        :dataSource="dataSource"
        :pagination="ipagination"
        :loading="loading"
        :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
        class="j-table-force-nowrap"
        @change="handleTableChange">

        <template slot="htmlSlot" slot-scope="text">
          <div v-html="text"></div>
        </template>
        <template slot="imgSlot" slot-scope="text">
          <span v-if="!text" style="font-size: 12px;font-style: italic;">无图片</span>
          <img v-else :src="getImgView(text)" height="25px" alt="" style="max-width:80px;font-size: 12px;font-style: italic;"/>
        </template>
        <template slot="fileSlot" slot-scope="text">
          <span v-if="!text" style="font-size: 12px;font-style: italic;">无文件</span>
          <a-button
            v-else
            :ghost="true"
            type="primary"
            icon="download"
            size="small"
            @click="downloadFile(text)">
            下载
          </a-button>
        </template>

        <span slot="action" slot-scope="text, record">
          <a @click="handleEdit(record)">编辑</a>

          <a-divider type="vertical" />
          <a-dropdown>
            <a class="ant-dropdown-link">更多 <a-icon type="down" /></a>
            <a-menu slot="overlay">
              <a-menu-item>
                <a @click="handleDetail(record)">详情</a>
              </a-menu-item>
              <a-menu-item v-if="record.actStatus === '未提交'">
                <a @click="apply(record)">提交申请</a>
              </a-menu-item>
              <a-menu-item>
                <a-popconfirm title="确定删除吗?" @confirm="() => handleDelete(record.id)">
                  <a>删除</a>
                </a-popconfirm>
              </a-menu-item>
            </a-menu>
          </a-dropdown>
        </span>

      </a-table>
    </div>

    <!--提交申请表单-->
    <a-modal title="提交申请" v-model="modalVisible" :mask-closable="false" :width="500" :footer="null">
      <div v-if="modalVisible">
        <a-form-item label="选择审批人" v-show="showAssign">
          <a-select style="width: 100%"
                    v-model="form.assignees"
                    placeholder="请选择"
                    mode="multiple"
                    :allowClear="true"
          >
            <a-select-option v-for="(item, i) in assigneeList" :key="i" :value="item.username">{{item.realname}}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="下一审批人" v-show="isGateway">
          <a-alert  type="info" showIcon message="分支网关处不支持自定义选择下一审批人，将自动下发给所有可审批人。">，将发送给下一节点所有人</a-alert>
        </a-form-item>
        <a-form-item label="优先级" prop="priority">
          <a-select v-model="form.priority" placeholder="请选择" :allowClear="true" style="width: 100%">
            <a-select-option :value="0">普通</a-select-option>
            <a-select-option :value="1">重要</a-select-option>
            <a-select-option :value="2">紧急</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="消息通知">
          <a-checkbox v-model="form.sendMessage">站内消息通知</a-checkbox>
          <a-checkbox v-model="form.sendSms" disabled>短信通知</a-checkbox>
          <a-checkbox v-model="form.sendEmail" disabled>邮件通知</a-checkbox>
        </a-form-item>
        <div slot="footer">
          <a-button type="text" @click="modalVisible=false">取消</a-button>
          <div style="display:inline-block;width: 20px;"></div>
          <a-button type="primary" :disabled="submitLoading" @click="applySubmit">提交</a-button>
        </div>
      </div>
    </a-modal>

    <approval-modal ref="modalForm" @ok="modalFormOk"></approval-modal>
  </a-card>
</template>

<script>

  import '@/assets/less/TableExpand.less'
  import { mixinDevice } from '@/utils/mixin'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import ApprovalModal from './modules/ApprovalModal'
  import { getAction, postFormAction } from '@/api/manage'

  export default {
    name: 'ApprovalList',
    mixins:[JeecgListMixin, mixinDevice],
    components: {
      ApprovalModal
    },
    data () {
      return {
        description: '审批表管理页面',
        // 表头
        columns: [
          {
            title: '#',
            dataIndex: '',
            key:'rowIndex',
            width:60,
            align:"center",
            customRender:function (t,r,index) {
              return parseInt(index)+1;
            }
          },
          {
            title:'姓名',
            align:"center",
            dataIndex: 'name'
          },
          {
            title:'部门',
            align:"center",
            dataIndex: 'dept_dictText'
          },
          {
            title:'老师',
            align:"center",
            dataIndex: 'teacher_dictText'
          },
          {
            title:'审核状态',
            align:"center",
            dataIndex: 'actStatus'
          },
          {
            title: '操作',
            dataIndex: 'action',
            align:"center",
            fixed:"right",
            width:147,
            scopedSlots: { customRender: 'action' }
          }
        ],
        url: {
          list: "/singletabledemo/approval/list",
          delete: "/singletabledemo/approval/delete",
          deleteBatch: "/singletabledemo/approval/deleteBatch",
          exportXlsUrl: "/singletabledemo/approval/exportXls",
          importExcelUrl: "singletabledemo/approval/importExcel",
          getActBusinessByTableInfo: "/actBusiness/getActBusinessByTableInfo",
          getFirstNode:'/actProcessIns/getFirstNode',
          applyBusiness:'/actBusiness/apply'
        },
        dictOptions:{},
        superFieldList:[],
        form:{
          priority:0,
          assignees:[],
          sendMessage:true
        },
        isGateway: false,
        showAssign: false,
        modalVisible: false,
        error: "",
        assigneeList: [],
        submitLoading: false,
      }
    },
    created() {
      this.getSuperFieldList();
    },
    computed: {
      importExcelUrl: function(){
        return `${window._CONFIG['domianURL']}/${this.url.importExcelUrl}`;
      },
    },
    methods: {
      apply(record) {
        getAction(this.url.getActBusinessByTableInfo, {tableName: "approval", tableId: record.id}).then(res => {
          if (res.success) {
            let v = res.result;
            if (!v) {
              this.$message.error("该记录无工单,请删除该记录重新提交");
              return;
            }
            if (!v.procDefId || v.procDefId == "null") {
              this.$message.error("流程定义为空");
              return;
            }
            this.form.id = v.id;
            this.form.procDefId = v.procDefId;
            this.form.title = v.title;

            // 加载审批人
            getAction(this.url.getFirstNode,{procDefId:v.procDefId,tableId:v.tableId,tableName:v.tableName}).then(res => {
              if (res.success) {
                if (res.result.type == 3 || res.result.type == 4) {
                  this.isGateway = true;
                  this.modalVisible = true;
                  this.form.firstGateway = true;
                  this.showAssign = false;
                  this.error = "";
                  return;
                }
                this.form.firstGateway = false;
                this.isGateway = false;
                if (res.result.users && res.result.users.length > 0) {
                  this.error = "";
                  this.assigneeList = res.result.users;
                  // 默认勾选
                  let ids = [];
                  res.result.users.forEach(e => {
                    ids.push(e.username);
                  });
                  this.form.assignees = ids;
                  this.showAssign = true;
                } else {
                  this.form.assignees = [];
                  this.showAssign = true;
                  this.error = '审批节点未分配候选审批人员，请联系管理员！';
                }
                if (this.error){
                  this.$message.error(this.error)
                  return;
                }
                this.modalVisible = true;
              }else {
                this.$message.error(res.message)
              }
            });
          }
        });
      },
      applySubmit() {
        if (this.showAssign && this.form.assignees.length < 1) {
          this.error = "请至少选择一个审批人";
          this.$message.error(this.error)
          return;
        } else {
          this.error = "";
        }
        this.submitLoading = true;
        var params = Object.assign({},this.form);
        params.assignees = params.assignees.join(",")
        postFormAction(this.url.applyBusiness,params).then(res => {
          if (res.success) {
            this.$message.success("操作成功, 请前往我的申请中查看详情");
            this.loadData();
            this.modalVisible = false;
          }else {
            this.$message.error(res.message)
          }
        }).finally(()=>this.submitLoading = false);
      },
      initDictConfig(){
      },
      getSuperFieldList(){
        let fieldList=[];
        fieldList.push({type:'string',value:'name',text:'姓名',dictCode:''})
        fieldList.push({type:'sel_depart',value:'dept',text:'部门'})
        fieldList.push({type:'sel_user',value:'teacher',text:'老师'})
        fieldList.push({type:'string',value:'actStatus',text:'审核状态',dictCode:''})
        this.superFieldList = fieldList
      }
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less';
</style>
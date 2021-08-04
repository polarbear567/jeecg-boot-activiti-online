<template>
  <a-card :bordered="false">
    <!-- 查询区域 -->
    <div class="table-page-search-wrapper">
      <a-form layout="inline" @keyup.enter.native="searchQueryByName">
        <a-row :gutter="24">

          <a-col :md="6" :sm="8">
            <a-form-item label="表单组件名称">
              <a-input placeholder="请输入搜索关键词" v-model="queryParam.keyWord"></a-input>
            </a-form-item>
          </a-col>

          <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
            <a-col :md="6" :sm="12" >
                <a-button type="primary"  style="left: 10px" @click="searchQueryByName" icon="search">查询</a-button>
                <a-button type="primary"  @click="searchReset" icon="reload" style="margin-left: 8px;left: 10px">重置</a-button>
            </a-col>
          </span>
          <span style="float: right;overflow: hidden;" class="table-page-search-submitButtons">
            <a-col :md="12" :sm="12" >
                <a-button type="primary"  style="left: 10px" @click="createObj.title = '添加表单组件';addFormComponent();" icon="search">添加表单组件</a-button>
            </a-col>
          </span>

        </a-row>
      </a-form>
    </div>

    <!-- table区域-begin -->
    <a-table :scroll="{x:true}" bordered
      ref="table"
      size="middle"
      rowKey="id"
      :columns="columns"
      :dataSource="dataSource"
      :pagination="ipagination"
      :loading="loading"
      @change="handleTableChange">
      <span slot="type" slot-scope="text">
        <span v-if="text === '1'">单表</span>
        <span v-if="text === '2'">单表(树)</span>
        <span v-if="text === '3'">主表</span>
      </span>
      <span slot="make" slot-scope="text, record">
        <a href="javascript:void(0);" @click="createObj.title = '编辑表单组件';editFormComponent(record)" >编辑</a>
        <a-divider type="vertical" />
        <a-popconfirm
          title="是否确认删除?"
          @confirm="handleDelete(record.id)"
        >
          <a>删除</a>
        </a-popconfirm>

      </span>
    </a-table>
    <!-- table区域-end -->
    <a-modal
      :title="createObj.title"
      :visible="createObj.visible"
      @ok="submit"
      :confirmLoading="createObj.confirmLoading"
      @cancel="createObj.visible = false"
    >
      <a-form :form="createForm" v-if="createObj.visible">
        <a-form-item :label-col="labelCol" :wrapper-col="wrapperCol" label="表单组件名称" >
          <a-input placeholder="输入表单组件名称" v-decorator="['text']"></a-input>
        </a-form-item>
        <a-form-item :label-col="labelCol" :wrapper-col="wrapperCol" label="表单组件路由" >
          <a-input placeholder="输入表单组件路由" v-decorator="['routeName']"></a-input>
        </a-form-item>
        <a-form-item :label-col="labelCol" :wrapper-col="wrapperCol" label="表单组件路径" >
          <a-input placeholder="输入表单组件路径" v-decorator="['component']"></a-input>
        </a-form-item>
        <a-form-item :label-col="labelCol" :wrapper-col="wrapperCol" label="业务表名称" >
          <a-input placeholder="输入业务表名称" v-decorator="['businessTable']"></a-input>
        </a-form-item>
        <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="业务表类型" >
          <a-select v-decorator="['tableType', {rules: [{ required: true, message: '不能为空' }]}]" placeholder="请选择业务表类型">
            <a-select-option value="1">单表</a-select-option>
            <a-select-option value="2">单表(树)</a-select-option>
            <a-select-option value="3">主表</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label-col="labelCol" :wrapper-col="wrapperCol" label="其他信息" >
          <a-input placeholder="请根据表类型填写" v-decorator="['otherInfo']"></a-input>
          <p>如果类型为<strong>单表</strong>则不用填,如果类型为<strong>单表(树)</strong>则填写对应controller下的删除api(代码生成的List vue文件url中也有)，示例: /xxx/delete,如果类型为<strong>主表</strong>则填写子表与外键，示例: subTable1:fk_id1,subTable2:fk_id2</p><br/>
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>

</template>

<script>
  import pick from 'lodash.pick'
  import { filterObj } from '@/utils/util';
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import { getAction,httpAction } from "@/api/manage"

  export default {
    name: "formCompoent",
    mixins:[JeecgListMixin],
    components: {
      JEllipsis
    },
    data () {
      return {
        createForm: this.$form.createForm(this),
        model: {},
        createObj:{
          title: null,
          visible: false,
          confirmLoading: false,
        },
        description: '这是表单组件列表页面',
        // 查询条件
        queryParam: {
          createTimeRange:[],
          keyWord:'',
        },
        tabKey: "1",
        // 表头
        columns: [
          {
            title: '#', width:50,
            dataIndex: '',
            key:'rowIndex',
            align:"center",
            customRender:function (t,r,index) {
              return parseInt(index)+1;
            }
          },
          {
            title: '表单组件名称', width:150,
            align:"center",
            dataIndex: 'text',
          },
          {
            title: '表单组件路由', width:150,
            dataIndex: 'routeName',
            align:"center",
          },
          {
            title: '表单组件路径', width:80,
            dataIndex: 'component',
            align:"center",
          },
          {
            title: '业务表名称', width:150,
            dataIndex: 'businessTable',
            align:"center",
          },
          {
            title: '业务表类型', width:150,
            dataIndex: 'tableType',
            align:"center",
            scopedSlots: { customRender: 'type' },
          },
          {
            title: '其他信息', width:150,
            dataIndex: 'otherInfo',
            align:"center",
          },
          {
            title: '操作',width:250,
            dataIndex: '',
            scopedSlots: { customRender: 'make' },
            align:"center",
          }
        ],
        labelCol: {
          xs: { span: 30 },
          sm: { span: 8 },
        },
        wrapperCol: {
          xs: { span: 24 },
          sm: { span: 16 },
        },
        url: {
          list: "/activiti/formComponent/list",
          query: "/activiti/formComponent/query",
          delete: "/activiti/formComponent/delete",
          add: "/activiti/formComponent/add",
          edit: "/activiti/formComponent/edit",
        },
      }
    },
    methods: {
      addFormComponent() {
        this.editFormComponent({});
      },
      editFormComponent(record) {
        //this.createObj.title = "编辑表单组件";
        this.createObj.visible=true;
        this.createForm.resetFields();
        this.model = Object.assign({}, record);
        this.$nextTick(() => {
          this.createForm.setFieldsValue(pick(this.model, 'text', 'routeName', 'component', 'businessTable', 'tableType', 'otherInfo'));
        })
      },
      submit() {
        this.createForm.validateFields((err, values) => {
          if (!err) {
            this.createObj.confirmLoading = true;
            let httpurl = '';
            let method = '';
            if(!this.model.id){
              httpurl+=this.url.add;
              method = 'post';
            }else{
              httpurl+=this.url.edit;
              method = 'put';
            }
            let formData = Object.assign(this.model, values);
            console.log("表单提交数据",formData)
            httpAction(httpurl,formData,method).then((res)=>{
              if(res.success){
                this.$message.success(res.message);
                this.$emit('ok');
              }else{
                this.$message.warning(res.message);
              }
            }).finally(() => {
              this.createObj.confirmLoading = false;
              this.createObj.visible=false;
              this.loadData();
            })
          }
        })
      },
      handleTableChange(pagination, filters, sorter) {
        //分页、排序、筛选变化时触发
        //TODO 筛选
        if (Object.keys(sorter).length > 0) {
          this.isorter.column = sorter.field;
          this.isorter.order = "ascend" == sorter.order ? "asc" : "desc"
        }
        this.ipagination = pagination;
        // this.loadData();
      },
      searchQueryByName() {
        //加载数据 若传入参数1则加载第一页的内容
        if (arg === 1) {
          this.ipagination.current = 1;
        }
        var params = this.getQueryParams();//查询条件
        this.loading = true;
        getAction(this.url.query, params).then((res) => {
          if (res.success) {
            let records = res.result||[];
            this.dataSource = records;
            this.ipagination.total = records.length;
          }
          if(res.code===510){
            this.$message.warning(res.message)
          }
          this.loading = false;
        })
      },
      loadData(arg) {
        if(!this.url.list){
          this.$message.error("请设置url.list属性!")
          return
        }
        //加载数据 若传入参数1则加载第一页的内容
        if (arg === 1) {
          this.ipagination.current = 1;
        }
        var params = this.getQueryParams();//查询条件
        this.loading = true;
        getAction(this.url.list, params).then((res) => {
          if (res.success) {
            let records = res.result||[];
            this.dataSource = records;
            this.ipagination.total = records.length;
          }
          if(res.code===510){
            this.$message.warning(res.message)
          }
          this.loading = false;
        })
      },
      getQueryParams(){
        var param = Object.assign({}, this.queryParam,this.isorter);
        delete param.createTimeRange; // 时间参数不传递后台
        return filterObj(param);
      },

      // 重置
      searchReset(){
        var logType = this.queryParam.logType;
        this.queryParam = {}; //清空查询区域参数
        this.queryParam.logType = logType;
        this.loadData(this.ipagination.current);
      }
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less';
</style>
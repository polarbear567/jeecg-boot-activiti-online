<template>
  <div class="form-main">
    <a-card :body-style="{padding: '24px 32px'}" :bordered="false">
      <a-form @submit="handleSubmit" :form="form">
        <a-row>
                      <a-col :span="12">
                        <a-form-item label="姓名" :labelCol="labelCol" :wrapperCol="wrapperCol">
                          <a-input :disabled="disabled" v-decorator="['name']" placeholder="请输入姓名"  ></a-input>
                        </a-form-item>
                      </a-col>
                      <a-col :span="12">
                        <a-form-item label="部门" :labelCol="labelCol" :wrapperCol="wrapperCol">
                          <j-select-depart :disabled="disabled" v-decorator="['dept']" multi  />
                        </a-form-item>
                      </a-col>
                      <a-col :span="12">
                        <a-form-item label="老师" :labelCol="labelCol" :wrapperCol="wrapperCol">
                          <j-select-user-by-dep :disabled="disabled" v-decorator="['teacher']" />
                        </a-form-item>
                      </a-col>
        </a-row>
        <a-form-item v-if="!disabled"
                     :wrapperCol="{ span: 24 }"
                     style="text-align: center"
        >
          <a-button type="primary" :disabled="disabled||btndisabled" @click="handleSubmit">保存</a-button>
          <a-button style="margin-left: 8px" :disabled="disabled" @click="close">取消</a-button>
        </a-form-item>
        <a-form-item v-if="task"
                     :wrapperCol="{ span: 24 }"
                     style="text-align: center"
        >
          <a-button type="primary"  @click="passTask">通过</a-button>
          <a-button style="margin-left: 8px"  @click="backTask">驳回</a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script>

  import { getAction,postFormAction} from "@/api/manage"
  import pick from 'lodash.pick'

  export default {
    name: 'ApprovalActForm',
    components: {
    },
    props: {
      //表单禁用
      disabled: {
        type: Boolean,
        default: false,
        required: false
      },
      processData:{
        type:Object,
        default:()=>{return {}},
        required:false
      },
      /*是否新增*/
      isNew: {type: Boolean, default: false, required: false},
      /*是否处理流程*/
      task: {type: Boolean, default: false, required: false}
    },
    data () {
      return {
        form: this.$form.createForm(this),
        labelCol: {
          xs: { span: 24 },
          sm: { span: 5 },
        },
        wrapperCol: {
          xs: { span: 24 },
          sm: { span: 16 },
        },
        validatorRules: {
        },
        url: {
          getForm:'/actBusiness/getForm',
          addApply:'/actBusiness/add',
          editForm:'/actBusiness/editForm',
        },
        /*表单回显数据*/
        data:{},
        btndisabled: false
      }
    },
    created () {
      if (!this.isNew){
        this.init();
      }
    },
    methods: {
      /*回显数据*/
      init(){
        this.btndisabled = true;
        var r = this.processData;
        getAction(this.url.getForm,{
          tableId:r.tableId,
          tableName:r.tableName,
        }).then((res)=>{
          if (res.success){
            let formData = res.result;
            formData.tableName = r.tableName;
            this.data = formData;
            console.log("表单回显数据",this.data)
            this.$nextTick(() => {
              this.form.setFieldsValue(pick(this.data,'name','dept','teacher','actStatus'))
            });
            this.btndisabled = false;
          }else {
            this.$message.error(res.message)
          }
        })
      },
      // handler
      handleSubmit (e) {
        e.preventDefault()
        this.form.validateFields((err, values) => {
          if (!err) {
            let formData = Object.assign(this.data||{}, values)
            formData.procDefId = this.processData.id;
            formData.procDeTitle = this.processData.name;
            if (!formData.tableName)formData.tableName = this.processData.businessTable;
            formData.filedNames = _.keys(values).join(",");
            console.log('formData', values)

            var url = this.url.addApply;
            if (!this.isNew){
              url = this.url.editForm;
            }
            this.btndisabled = true;
            postFormAction(url,formData).then((res)=>{
              if (res.success){
                this.$message.success("保存成功！")
                //todo 将表单的数据传给父组件
                this.$emit('afterSubmit',formData)
              }else {
                this.$message.error(res.message)
              }
            }).finally(()=>{
              this.btndisabled = false;
            })
          }
        })
      },
      close() {
        //todo 关闭后的回调
        this.$emit('close')
      },
      /*通过审批*/
      passTask() {
        this.$emit('passTask')
      },
      /*驳回审批*/
      backTask() {
        this.$emit('backTask')
      },
      popupCallback(row){
        this.form.setFieldsValue(pick(row,'name','dept','teacher','actStatus'))
      },
    }
  }
</script>

<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ recovery: Record<string, unknown> }>();
const text = (field: string) => String(props.recovery[field] || '未提供（旧版申请）');
const hints = computed(() =>
  Array.isArray(props.recovery.historicalHints) ? (props.recovery.historicalHints as string[]) : [],
);
</script>
<template>
  <section class="recovery-details">
    <div class="form-grid">
      <div>
        <small>用户名 / 账号身份</small>
        <p>
          {{ text('account') }} /
          {{
            recovery.role === 'ORGANIZER'
              ? '组织者'
              : recovery.role === 'PARTICIPANT'
                ? '参与者'
                : '未记录'
          }}
        </p>
      </div>
      <div>
        <small>账户原手机号</small>
        <p>{{ text('phone') }}</p>
      </div>
      <div>
        <small>当前手机号（通知临时凭据）</small>
        <p>{{ text('currentPhone') }}</p>
      </div>
      <div>
        <small>当前号码验证</small>
        <p>{{ recovery.contactVerifiedAt ? '提交时已通过验证码验证' : '旧版申请未验证' }}</p>
      </div>
    </div>
    <h4>申请人陈述 · 尚需人工核实</h4>
    <p class="preserve-lines">{{ text('statement') }}</p>
    <h4>申请人提供的以往行为信息</h4>
    <p class="preserve-lines">{{ text('pastBehavior') }}</p>
    <h4>系统匹配的原账号及历史摘要</h4>
    <p v-if="!recovery.targetAccountId" class="field-help">
      未同时匹配用户名、原手机号和角色，不能审核通过或重置；请核对申请资料。
    </p>
    <ul v-else>
      <li v-for="hint in hints" :key="hint">{{ hint }}</li>
    </ul>
    <p class="field-help">
      摘要仅限该账号自身最近最多5笔订单或自建活动，不是活动参与者名单。知道用户名和历史信息不等于已经证明本人身份，请在核实后填写审核依据。
    </p>
  </section>
</template>

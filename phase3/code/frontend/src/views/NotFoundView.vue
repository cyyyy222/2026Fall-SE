<script setup lang="ts">
import { useRoute } from 'vue-router';
import { useAppStore } from '../stores/app';
import StatePanel from '../components/StatePanel.vue';
const route = useRoute();
const app = useAppStore();
</script>
<template>
  <StatePanel
    :title="route.meta.forbidden ? '这个空间属于另一种身份' : '这条路暂时没有通向现场'"
    :description="
      route.meta.forbidden
        ? '当前账号没有此页面的访问权限。账号角色固定；如需体验其他角色，请退出后使用对应演示账号登录。'
        : '页面地址可能有误，回到发现活动，继续下一场期待。'
    "
    :icon="route.meta.forbidden ? 'ShieldCheck' : 'MapPin'"
    ><RouterLink :to="app.home" class="btn primary">返回我的首页</RouterLink></StatePanel
  >
</template>

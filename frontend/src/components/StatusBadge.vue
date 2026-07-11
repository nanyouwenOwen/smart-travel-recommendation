<script setup lang="ts">defineProps<{status:string}>(); const labels:Record<string,string>={DRAFT:'草稿',GENERATING:'生成中',READY:'已就绪',FAILED:'失败',PENDING:'等待中',STREAMING:'回答中',COMPLETED:'已完成'}</script>
<template><span class="badge" :class="`badge-${status.toLowerCase()}`">{{labels[status]??status}}</span></template>

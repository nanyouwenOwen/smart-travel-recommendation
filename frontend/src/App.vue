<script setup lang="ts">import { RouterView } from 'vue-router'; import AppShell from '@/layouts/AppShell.vue'; import { useAuthStore } from '@/stores/auth'; const auth=useAuthStore()</script>

<template>
  <AppShell v-if="auth.authenticated"><RouterView /></AppShell>
  <RouterView v-else />
</template>

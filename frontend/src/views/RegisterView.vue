<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { ApiError } from "@/api/client";
const displayName = ref(""),
  email = ref(""),
  password = ref(""),
  error = ref(""),
  fields = ref<Record<string, string>>({}),
  busy = ref(false),
  auth = useAuthStore(),
  router = useRouter();
async function submit() {
  if (new TextEncoder().encode(password.value).length > 72) {
    error.value = "密码 UTF-8 长度不能超过 72 字节";
    return;
  }
  busy.value = true;
  error.value = "";
  fields.value = {};
  try {
    await auth.register(email.value, password.value, displayName.value);
    await router.push("/trips");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "注册失败";
    if (e instanceof ApiError)
      fields.value = Object.fromEntries(
        (e.details ?? [])
          .filter((x) => x.field && x.reason)
          .map((x) => [x.field!, x.reason!]),
      );
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="auth-page">
    <RouterLink class="brand" to="/">旅途智囊</RouterLink>
    <form class="auth-card" @submit.prevent="submit">
      <p class="eyebrow">START EXPLORING</p>
      <h1 tabindex="-1">创建账户</h1>
      <label
        >昵称<input
          v-model.trim="displayName"
          :aria-invalid="!!fields.displayName"
          required
          maxlength="50"
          autocomplete="name"
        /><small v-if="fields.displayName" class="form-error">{{
          fields.displayName
        }}</small></label
      ><label
        >邮箱<input
          v-model.trim="email"
          type="email"
          :aria-invalid="!!fields.email"
          required
          maxlength="254"
          autocomplete="email"
        /><small v-if="fields.email" class="form-error">{{
          fields.email
        }}</small></label
      ><label
        >密码<input
          v-model="password"
          type="password"
          :aria-invalid="!!fields.password"
          required
          minlength="8"
          maxlength="72"
          autocomplete="new-password"
        /><small v-if="fields.password" class="form-error">{{
          fields.password
        }}</small
        ><small v-else>8–72 个字符</small></label
      >
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button :disabled="busy">{{ busy ? "创建中…" : "注册并开始" }}</button>
      <p>已有账户？<RouterLink to="/login">登录</RouterLink></p>
    </form>
  </main>
</template>

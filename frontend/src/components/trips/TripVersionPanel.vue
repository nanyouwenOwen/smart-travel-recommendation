<script setup lang="ts">
import { ref } from "vue";
import { useTripStore } from "@/stores/trips";
import { formatMoney } from "@/utils/money";
import ConfirmDialog from "@/components/ConfirmDialog.vue";
const store = useTripStore(),
  instruction = ref(""),
  error = ref(""),
  dialog = ref<InstanceType<typeof ConfirmDialog>>();
async function adjust() {
  error.value = "";
  try {
    await store.adjust(instruction.value);
    instruction.value = "";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "调整失败";
  }
}
async function restore(v: number) {
  if (await dialog.value?.confirm(`确定恢复到版本 v${v}？`))
    await store.restore(v);
}
</script>
<template>
  <section class="panel">
    <p class="eyebrow">REFINE</p>
    <h2>调整与历史版本</h2>
    <form @submit.prevent="adjust">
      <label
        >用自然语言调整<textarea
          v-model.trim="instruction"
          minlength="2"
          maxlength="2000"
          required
          rows="3"
          :disabled="store.adjustmentPending"
          placeholder="例如：第二天下午改成适合雨天的室内活动"
        ></textarea>
      </label>
      <p v-if="error || store.adjustmentError" class="form-error" role="alert">
        {{ error || store.adjustmentError }}
      </p>
      <p v-if="store.adjustmentPending" class="muted" role="status">
        新版本生成中，当前版本仍可正常查看。
      </p>
      <button :disabled="store.adjustmentPending">
        {{ store.adjustmentPending ? "新版本生成中…" : "生成新版本" }}
      </button>
    </form>
    <div class="version-list">
      <button
        v-for="version in store.versions"
        :key="version.versionNumber"
        class="version"
        @click="store.loadVersion(version.versionNumber)"
      >
        <span
          >v{{ version.versionNumber }} ·
          {{ new Date(version.createdAt).toLocaleDateString() }}</span
        ><b>{{ formatMoney(version.estimatedTotal) }}</b>
      </button>
    </div>
    <div v-if="store.preview" class="preview">
      <h3>版本 v{{ store.preview.versionNumber }}</h3>
      <p>
        {{ store.preview.itinerary.length }} 天 ·
        {{ formatMoney(store.preview.estimatedTotal) }}
      </p>
      <button
        v-if="store.preview.versionNumber !== store.current?.currentVersion"
        class="secondary"
        @click="restore(store.preview.versionNumber)"
      >
        恢复此版本
      </button>
    </div>
    <ConfirmDialog ref="dialog" />
  </section>
</template>

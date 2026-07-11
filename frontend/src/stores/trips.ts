import { defineStore } from "pinia";
import { computed, reactive, ref } from "vue";
import { tripApi } from "@/api/trips";
import type {
  CreateTripInput,
  Trip,
  TripSummary,
  TripVersion,
  TripVersionSummary,
} from "@/api/types";
import { requestKey } from "@/utils/id";

export const useTripStore = defineStore("trips", () => {
  const items = ref<TripSummary[]>([]),
    current = ref<Trip | null>(null);
  const versions = ref<TripVersionSummary[]>([]),
    preview = ref<TripVersion | null>(null);
  const loading = ref(false),
    error = ref(""),
    nextCursor = ref<string>(),
    hasMore = ref(false);
  const adjustments = reactive(
    new Map<
      string,
      { baseline: number; pending: boolean; error: string; deadline: number }
    >(),
  );
  let poll: number | undefined,
    generation = 0;
  const adjustmentPending = computed(
    () => !!current.value && !!adjustments.get(current.value.id)?.pending,
  );
  const adjustmentError = computed(() =>
    current.value ? (adjustments.get(current.value.id)?.error ?? "") : "",
  );

  async function list(reset = true) {
    loading.value = reset && !items.value.length;
    error.value = "";
    try {
      const page = await tripApi.list(reset ? undefined : nextCursor.value);
      items.value = reset
        ? page.data
        : [
            ...new Map(
              [...items.value, ...page.data].map((x) => [x.id, x]),
            ).values(),
          ];
      nextCursor.value = page.meta.nextCursor;
      hasMore.value = !!page.meta.hasMore;
    } catch (e) {
      error.value = message(e);
    } finally {
      loading.value = false;
    }
  }

  async function load(id: string) {
    const mine = ++generation;
    stopPolling(false);
    loading.value = true;
    error.value = "";
    current.value = null;
    preview.value = null;
    versions.value = [];
    try {
      const loaded = await tripApi.get(id);
      if (mine !== generation) return;
      current.value = loaded;
      const adjustment = adjustments.get(id);
      if (adjustment?.pending) startPolling(id, adjustment);
      else if (loaded.status === "GENERATING") startPolling(id);
    } catch (e) {
      if (mine === generation) error.value = message(e);
    } finally {
      if (mine === generation) loading.value = false;
    }
  }

  async function create(input: CreateTripInput, key = requestKey()) {
    return tripApi.create(input, key);
  }

  function startPolling(
    id: string,
    adjustment?: {
      baseline: number;
      pending: boolean;
      error: string;
      deadline: number;
    },
  ) {
    const mine = generation,
      deadline = adjustment?.deadline ?? Date.now() + 120_000;
    let delay = 900;
    const run = async () => {
      if (mine !== generation) return;
      if (document.visibilityState === "hidden") {
        if (Date.now() >= deadline && adjustment) {
          adjustment.pending = false;
          adjustment.error = "等待新版本超时，原版本仍可使用，请稍后刷新";
          return;
        }
        poll = window.setTimeout(run, 500);
        return;
      }
      try {
        const loaded = await tripApi.get(id);
        if (mine !== generation) return;
        current.value = loaded;
        const versionAdvanced =
          !!adjustment && (loaded.currentVersion ?? 0) > adjustment.baseline;
        const initialFinished = !adjustment && loaded.status !== "GENERATING";
        const adjustmentFailed = !!adjustment && !!loaded.failureCode;
        const timedOut = Date.now() >= deadline;
        if (
          versionAdvanced ||
          initialFinished ||
          adjustmentFailed ||
          timedOut
        ) {
          if (versionAdvanced || initialFinished) {
            if (loaded.status === "READY") await loadVersions();
          }
          if (adjustment) {
            adjustment.pending = false;
            adjustment.error = adjustmentFailed
              ? `调整失败（${loaded.failureCode}），原版本仍可使用`
              : timedOut
                ? "等待新版本超时，原版本仍可使用，请稍后刷新"
                : "";
            if (versionAdvanced) adjustments.delete(id);
            return;
          }
        }
        delay = Math.min(5_000, Math.round(delay * 1.55 + Math.random() * 200));
      } catch {
        /* retain the last usable version during a transient error */
      }
      const remaining = deadline - Date.now();
      poll = window.setTimeout(run, Math.min(delay, Math.max(1, remaining)));
    };
    poll = window.setTimeout(run, delay);
  }

  function stopPolling(invalidate = true) {
    if (poll) window.clearTimeout(poll);
    poll = undefined;
    if (invalidate) generation++;
  }

  async function adjust(instruction: string) {
    if (!current.value || adjustmentPending.value) return;
    const id = current.value.id,
      baseline = current.value.currentVersion ?? 0,
      state = {
        baseline,
        pending: true,
        error: "",
        deadline: Date.now() + 120_000,
      };
    adjustments.set(id, state);
    try {
      const response = await tripApi.adjust(id, instruction);
      current.value =
        response.currentVersion && response.currentVersion > baseline
          ? response
          : current.value;
      startPolling(id, state);
    } catch (e) {
      state.pending = false;
      state.error = message(e);
      throw e;
    }
  }
  async function loadVersions() {
    if (current.value)
      versions.value = await tripApi.versions(current.value.id);
  }
  async function loadVersion(version: number) {
    if (current.value)
      preview.value = await tripApi.version(current.value.id, version);
  }
  async function restore(version: number) {
    if (current.value) {
      current.value = await tripApi.restore(current.value.id, version);
      await loadVersions();
    }
  }
  async function remove() {
    if (current.value) await tripApi.remove(current.value.id);
  }
  return {
    items,
    current,
    versions,
    preview,
    loading,
    error,
    hasMore,
    adjustmentPending,
    adjustmentError,
    list,
    load,
    create,
    stopPolling,
    adjust,
    loadVersions,
    loadVersion,
    restore,
    remove,
  };
});
const message = (e: unknown) => (e instanceof Error ? e.message : "操作失败");

import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useTripStore } from "./trips";
import { tripApi } from "@/api/trips";
import type { Trip } from "@/api/types";
vi.mock("@/api/trips", () => ({
  tripApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    adjust: vi.fn(),
    versions: vi.fn(),
    version: vi.fn(),
    restore: vi.fn(),
    remove: vi.fn(),
  },
}));
const trip = (id: string, version = 1, failureCode?: string): Trip => ({
  id,
  destination: id,
  startDate: "2026-08-01",
  days: 2,
  status: "READY",
  currentVersion: version,
  failureCode,
  createdAt: "now",
  budget: { amount: "1000.00", currency: "CNY" },
  travelers: 1,
  preferences: [],
  timezone: "Asia/Shanghai",
  itinerary: [],
});
describe("adjustment state isolation", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    vi.useFakeTimers();
    vi.mocked(tripApi.versions).mockResolvedValue([]);
  });
  it("keeps B usable and resumes A polling when returning", async () => {
    let aReads = 0;
    vi.mocked(tripApi.get).mockImplementation(async (id) =>
      id === "A" ? (++aReads >= 3 ? trip("A", 2) : trip("A")) : trip("B"),
    );
    vi.mocked(tripApi.adjust).mockResolvedValue(trip("A"));
    const store = useTripStore();
    await store.load("A");
    await store.adjust("调整A");
    store.stopPolling();
    await store.load("B");
    expect(store.adjustmentPending).toBe(false);
    expect(store.adjustmentError).toBe("");
    await store.load("A");
    expect(store.adjustmentPending).toBe(true);
    await vi.advanceTimersByTimeAsync(1200);
    expect(store.current?.currentVersion).toBe(2);
    expect(store.adjustmentPending).toBe(false);
  });
  it("does not leak A failure into B", async () => {
    vi.mocked(tripApi.get)
      .mockResolvedValueOnce(trip("A"))
      .mockResolvedValueOnce(trip("A", 1, "AI_UNAVAILABLE"))
      .mockResolvedValueOnce(trip("B"));
    vi.mocked(tripApi.adjust).mockResolvedValue(trip("A"));
    const store = useTripStore();
    await store.load("A");
    await store.adjust("调整A");
    await vi.advanceTimersByTimeAsync(1200);
    expect(store.adjustmentError).toContain("AI_UNAVAILABLE");
    await store.load("B");
    expect(store.adjustmentError).toBe("");
    expect(store.adjustmentPending).toBe(false);
  });
  it("tracks parallel A and B adjustments independently", async () => {
    const reads = { A: 0, B: 0 };
    vi.mocked(tripApi.get).mockImplementation(async (id) => {
      reads[id as "A" | "B"]++;
      return trip(id, reads[id as "A" | "B"] >= 3 ? 2 : 1);
    });
    vi.mocked(tripApi.adjust).mockImplementation(async (id) => trip(id));
    const store = useTripStore();
    await store.load("A");
    await store.adjust("调整A");
    store.stopPolling();
    await store.load("B");
    await store.adjust("调整B");
    store.stopPolling();
    await store.load("A");
    expect(store.adjustmentPending).toBe(true);
    await vi.advanceTimersByTimeAsync(1200);
    expect(store.current?.currentVersion).toBe(2);
    await store.load("B");
    expect(store.adjustmentPending).toBe(true);
    await vi.advanceTimersByTimeAsync(1200);
    expect(store.current?.currentVersion).toBe(2);
    expect(tripApi.adjust).toHaveBeenCalledTimes(2);
  });
  it("preserves the original absolute deadline across navigation", async () => {
    vi.mocked(tripApi.get).mockImplementation(async (id) => trip(id));
    vi.mocked(tripApi.adjust).mockResolvedValue(trip("A"));
    const store = useTripStore();
    await store.load("A");
    await store.adjust("调整A");
    store.stopPolling();
    await store.load("B");
    await vi.advanceTimersByTimeAsync(119_500);
    await store.load("A");
    await vi.advanceTimersByTimeAsync(1_000);
    expect(store.adjustmentPending).toBe(false);
    expect(store.adjustmentError).toContain("超时");
  });
});

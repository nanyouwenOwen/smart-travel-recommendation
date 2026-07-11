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
const ready: Trip = {
  id: "t1",
  destination: "京都",
  startDate: "2026-08-01",
  days: 2,
  status: "READY",
  createdAt: "now",
  budget: { amount: "1000.00", currency: "CNY" },
  travelers: 1,
  preferences: [],
  timezone: "Asia/Shanghai",
  itinerary: [],
};
describe("trip store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    vi.useRealTimers();
  });
  it("loads cursor pages and de-duplicates trips", async () => {
    vi.mocked(tripApi.list)
      .mockResolvedValueOnce({
        data: [ready],
        meta: { requestId: "1", hasMore: true, nextCursor: "next" },
      })
      .mockResolvedValueOnce({
        data: [ready, { ...ready, id: "t2" }],
        meta: { requestId: "2", hasMore: false },
      });
    const store = useTripStore();
    await store.list();
    await store.list(false);
    expect(store.items.map((x) => x.id)).toEqual(["t1", "t2"]);
    expect(store.hasMore).toBe(false);
  });
  it("loads versions and restores the selected version", async () => {
    vi.mocked(tripApi.get).mockResolvedValue(ready as never);
    vi.mocked(tripApi.versions).mockResolvedValue([
      {
        versionNumber: 1,
        estimatedTotal: { amount: "900.00", currency: "CNY" },
        createdAt: "now",
      },
    ]);
    vi.mocked(tripApi.restore).mockResolvedValue({
      ...ready,
      currentVersion: 1,
    } as never);
    const store = useTripStore();
    await store.load("t1");
    await store.loadVersions();
    await store.restore(1);
    expect(store.current?.currentVersion).toBe(1);
    expect(tripApi.restore).toHaveBeenCalledWith("t1", 1);
  });
  it("polls a READY trip until an adjustment advances the baseline version", async () => {
    vi.useFakeTimers();
    const v1 = { ...ready, currentVersion: 1 },
      v2 = { ...ready, currentVersion: 2 };
    vi.mocked(tripApi.get).mockResolvedValueOnce(v1).mockResolvedValueOnce(v2);
    vi.mocked(tripApi.adjust).mockResolvedValue(v1);
    vi.mocked(tripApi.versions).mockResolvedValue([]);
    const store = useTripStore();
    await store.load("t1");
    await store.adjust("增加室内活动");
    await vi.advanceTimersByTimeAsync(1500);
    expect(store.current?.currentVersion).toBe(2);
    expect(tripApi.versions).toHaveBeenCalled();
  });
  it("delegates create, preview and delete operations", async () => {
    vi.mocked(tripApi.create).mockResolvedValue(ready);
    vi.mocked(tripApi.get).mockResolvedValue(ready);
    vi.mocked(tripApi.version).mockResolvedValue({
      versionNumber: 1,
      estimatedTotal: ready.budget,
      createdAt: "now",
      itinerary: [],
      budgetBreakdown: {
        categories: [],
        total: ready.budget,
        exceedsBudget: false,
      },
    });
    const store = useTripStore();
    await store.create(
      {
        destination: "京都",
        startDate: "2026-08-01",
        days: 2,
        budget: ready.budget,
        travelers: 1,
        preferences: [],
        timezone: "Asia/Shanghai",
      },
      "stable-key",
    );
    await store.load("t1");
    await store.loadVersion(1);
    await store.remove();
    store.stopPolling();
    expect(tripApi.create).toHaveBeenCalledWith(
      expect.anything(),
      "stable-key",
    );
    expect(tripApi.remove).toHaveBeenCalledWith("t1");
  });
});

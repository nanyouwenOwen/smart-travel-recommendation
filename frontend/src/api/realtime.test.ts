import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./client";
import { realtimeApi } from "./realtime";

vi.mock("./client", () => ({ api: vi.fn() }));

describe("realtimeApi", () => {
  beforeEach(() => vi.clearAllMocks());
  it("encodes explicit location search and bounded trip endpoints", async () => {
    vi.mocked(api).mockResolvedValue([]);
    await realtimeApi.searchLocations("京都 市", "zh-CN", 5);
    await realtimeApi.weather("trip/id");
    await realtimeApi.places("trip/id", 7);
    expect(api).toHaveBeenNthCalledWith(
      1,
      "/locations/search?q=%E4%BA%AC%E9%83%BD%20%E5%B8%82&language=zh-CN&limit=5",
    );
    expect(api).toHaveBeenNthCalledWith(2, "/trips/trip%2Fid/realtime/weather");
    expect(api).toHaveBeenNthCalledWith(
      3,
      "/trips/trip%2Fid/realtime/places?category=ATTRACTION&limit=7",
    );
  });
});

import { describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import TripCreateView from "./TripCreateView.vue";
import { ApiError } from "@/api/client";
const { create, push, key } = vi.hoisted(() => ({
  create: vi.fn(),
  push: vi.fn(),
  key: vi.fn().mockReturnValueOnce("key-one").mockReturnValueOnce("key-two"),
}));
vi.mock("@/stores/trips", () => ({ useTripStore: () => ({ create }) }));
vi.mock("vue-router", () => ({ useRouter: () => ({ push }) }));
vi.mock("@/utils/id", () => ({ requestKey: key }));
describe("TripCreateView idempotency", () =>
  it("reuses the same key after an unknown network result while the form is unchanged", async () => {
    create.mockRejectedValue(new ApiError(0, "NETWORK_ERROR", "网络未知"));
    const wrapper = mount(TripCreateView);
    const inputs = wrapper.findAll("input");
    await inputs[0]!.setValue("京都");
    await wrapper.find("form").trigger("submit");
    await vi.waitFor(() => expect(create).toHaveBeenCalledTimes(1));
    await wrapper.find("form").trigger("submit");
    await vi.waitFor(() => expect(create).toHaveBeenCalledTimes(2));
    expect(create.mock.calls[0]?.[1]).toBe("key-one");
    expect(create.mock.calls[1]?.[1]).toBe("key-one");
    expect(key).toHaveBeenCalledOnce();
  }));

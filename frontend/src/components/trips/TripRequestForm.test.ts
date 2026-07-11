import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import TripRequestForm from "./TripRequestForm.vue";
describe("TripRequestForm", () => {
  it("rejects malformed money and emits contract-shaped input when valid", async () => {
    const wrapper = mount(TripRequestForm);
    const inputs = wrapper.findAll("input");
    await inputs[0]!.setValue("京都");
    await inputs[3]!.setValue("wrong");
    await wrapper.find("form").trigger("submit");
    expect(wrapper.text()).toContain("有效预算");
    await inputs[3]!.setValue("5000.00");
    await wrapper.find("form").trigger("submit");
    const value = wrapper.emitted("submit")?.[0]?.[0] as {
      destination: string;
      budget: { amount: string };
    };
    expect(value.destination).toBe("京都");
    expect(value.budget.amount).toBe("5000.00");
  });
  it("validates IANA timezone and focuses the failing field", async () => {
    const wrapper = mount(TripRequestForm, { attachTo: document.body });
    await wrapper.get("#destination").setValue("京都");
    await wrapper.get("#timezone").setValue("Not/AZone");
    await wrapper.find("form").trigger("submit");
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain("IANA 时区");
    expect(document.activeElement?.id).toBe("timezone");
    wrapper.unmount();
  });
  it("enforces per-preference length without truncating silently", async () => {
    const wrapper = mount(TripRequestForm);
    await wrapper.get("#destination").setValue("京都");
    await wrapper.get("#preferences").setValue("x".repeat(51));
    await wrapper.find("form").trigger("submit");
    expect(wrapper.text()).toContain("每项不超过 50 字");
  });
});

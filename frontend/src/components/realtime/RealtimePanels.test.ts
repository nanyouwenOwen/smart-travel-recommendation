import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import WeatherPanel from "./WeatherPanel.vue";
import NearbyPlacesPanel from "./NearbyPlacesPanel.vue";
const source = {
  provider: "OPEN_METEO",
  label: "Open-Meteo",
  sourceUrl: "https://open-meteo.com/",
  retrievedAt: "2026-07-12T00:00:00Z",
  freshness: "FRESH" as const,
};
describe("realtime panels", () => {
  it("shows measured weather and attribution", () => {
    const wrapper = mount(WeatherPanel, {
      props: {
        snapshot: {
          timezone: "Asia/Tokyo",
          days: [
            {
              date: "2026-07-12",
              weatherCode: 0,
              temperatureMin: 20,
              temperatureMax: 28,
            },
          ],
          unavailableDates: [],
          sources: [source],
          freshness: "FRESH",
        },
      },
    });
    expect(wrapper.text()).toContain("20° – 28°");
    expect(wrapper.text()).toContain("Open-Meteo");
  });
  it("does not claim missing opening hours mean always open", () => {
    const wrapper = mount(NearbyPlacesPanel, {
      props: {
        snapshot: {
          places: [
            {
              providerId: "n1",
              name: "博物馆",
              category: "MUSEUM",
              latitude: 1,
              longitude: 2,
            },
          ],
          sources: [],
          freshness: "FRESH",
        },
      },
    });
    expect(wrapper.text()).toContain("暂无数据，请向景点官方核验");
    expect(wrapper.text()).not.toContain("全天开放");
  });
});

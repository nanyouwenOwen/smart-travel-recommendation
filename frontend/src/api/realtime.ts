import { api } from "./client";
import type {
  LocationSearchResult,
  PlaceSnapshot,
  WeatherSnapshot,
} from "./types";

const enc = encodeURIComponent;
export const realtimeApi = {
  searchLocations: (query: string, language = "zh-CN", limit = 8) =>
    api<LocationSearchResult[]>(
      `/locations/search?q=${enc(query)}&language=${enc(language)}&limit=${limit}`,
    ),
  weather: (tripId: string) =>
    api<WeatherSnapshot>(`/trips/${enc(tripId)}/realtime/weather`),
  places: (tripId: string, limit = 12) =>
    api<PlaceSnapshot>(
      `/trips/${enc(tripId)}/realtime/places?category=ATTRACTION&limit=${limit}`,
    ),
};

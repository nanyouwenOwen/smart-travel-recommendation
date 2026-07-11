import type { Money } from "@/api/types";
export function formatMoney(value?: Money) {
  return value ? `${value.currency} ${value.amount}` : "—";
}
export const categoryLabel: Record<string, string> = {
  TRANSPORTATION: "交通",
  ACCOMMODATION: "住宿",
  FOOD: "餐饮",
  ATTRACTION: "景点",
  SHOPPING: "购物",
  OTHER: "其他",
};

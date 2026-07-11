<script setup lang="ts">
import type { Trip } from "@/api/types";
import { formatMoney, categoryLabel } from "@/utils/money";
defineProps<{ trip: Trip }>();
</script>
<template>
  <section class="panel">
    <div class="section-heading">
      <div>
        <p class="eyebrow">BUDGET</p>
        <h2>预算概览</h2>
      </div>
      <span class="estimate">AI 估算 · 非实时</span>
    </div>
    <div class="budget-total">
      <div>
        <small>团队总预算</small><strong>{{ formatMoney(trip.budget) }}</strong>
      </div>
      <div>
        <small>预计总花费</small
        ><strong>{{
          formatMoney(trip.estimatedTotal ?? trip.budgetBreakdown?.total)
        }}</strong>
      </div>
    </div>
    <p v-if="trip.budgetBreakdown?.exceedsBudget" class="banner warning">
      预计超出预算 {{ formatMoney(trip.budgetBreakdown.exceededBy) }}
    </p>
    <ul class="budget-list">
      <li v-for="item in trip.budgetBreakdown?.categories" :key="item.category">
        <span>{{ categoryLabel[item.category] }}</span
        ><b>{{ formatMoney(item.amount) }}</b>
      </li>
    </ul>
  </section>
</template>

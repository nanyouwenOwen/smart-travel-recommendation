<script setup lang="ts">
import type { ItineraryDay } from "@/api/types";
import { formatMoney, categoryLabel } from "@/utils/money";
defineProps<{ days: ItineraryDay[] }>();
</script>
<template>
  <section>
    <div class="section-heading">
      <div>
        <p class="eyebrow">ITINERARY</p>
        <h2>逐日安排</h2>
      </div>
    </div>
    <article v-for="day in days" :key="day.dayNumber" class="day">
      <header>
        <span>DAY {{ String(day.dayNumber).padStart(2, "0") }}</span>
        <div>
          <h3>{{ day.date }}</h3>
          <p v-if="day.summary">{{ day.summary }}</p>
        </div>
      </header>
      <ol>
        <li v-for="activity in day.activities" :key="activity.sequenceNumber">
          <time>{{ activity.startTime }}–{{ activity.endTime }}</time>
          <div>
            <div class="activity-title">
              <h4>{{ activity.title }}</h4>
              <span>{{ categoryLabel[activity.category] }}</span>
            </div>
            <p class="location">⌖ {{ activity.location }}</p>
            <p v-if="activity.description">{{ activity.description }}</p>
            <p v-if="activity.transportAdvice" class="advice">
              交通建议：{{ activity.transportAdvice }}
            </p>
          </div>
          <b>{{ formatMoney(activity.estimatedCost) }}</b>
        </li>
      </ol>
    </article>
  </section>
</template>

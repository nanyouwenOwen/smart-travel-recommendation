import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "home",
      component: () => import("@/views/HomeView.vue"),
    },
    {
      path: "/login",
      name: "login",
      component: () => import("@/views/LoginView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/register",
      name: "register",
      component: () => import("@/views/RegisterView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/trips",
      name: "trips",
      component: () => import("@/views/TripListView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/trips/new",
      name: "trip-new",
      component: () => import("@/views/TripCreateView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/trips/:tripId",
      name: "trip-detail",
      component: () => import("@/views/TripDetailView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/conversations",
      name: "conversations",
      component: () => import("@/views/ConversationListView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/conversations/:conversationId",
      name: "conversation",
      component: () => import("@/views/ConversationView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/:pathMatch(.*)*",
      name: "not-found",
      component: () => import("@/views/NotFoundView.vue"),
    },
  ],
});
router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.authenticated)
    return { name: "login", query: { redirect: to.fullPath } };
  if (to.meta.guestOnly && auth.authenticated) return { name: "trips" };
});
router.afterEach(() =>
  requestAnimationFrame(() =>
    (document.querySelector("h1") as HTMLElement | null)?.focus(),
  ),
);
export default router;

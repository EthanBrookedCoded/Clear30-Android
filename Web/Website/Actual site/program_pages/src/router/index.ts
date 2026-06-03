import { createRouter, createWebHashHistory, RouteRecordRaw } from "vue-router";
import HomeView from "../views/HomeView.vue";

const routes: Array<RouteRecordRaw> = [
  {
    path: "/:arg?",
    name: "home",
    component: HomeView,
    alias: "/home",
  },
  {
    path: "/:fileName",
    name: "program",
    component: () => import("../views/ProgramView.vue"),
  },
  {
    path: "/:catchAll(.*)",
    redirect: "/",
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    window.scrollTo(0, 0);
  },
});

export default router;

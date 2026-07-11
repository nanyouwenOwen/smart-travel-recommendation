import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/main.css'
import './styles/responsive.css'
import { useAuthStore } from './stores/auth'

const pinia = createPinia()
const app = createApp(App).use(pinia)
await useAuthStore(pinia).initialize()
app.use(router).mount('#app')
window.addEventListener('auth:expired',()=>{if(router.currentRoute.value.meta.requiresAuth)void router.replace({name:'login',query:{redirect:router.currentRoute.value.fullPath}})})

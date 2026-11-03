<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { get, query } from '../api/client';
import type { Category, Page, Session } from '../types';
import { date, money, time } from '../utils/format';
import AppIcon from '../components/AppIcon.vue';
import PosterArt from '../components/PosterArt.vue';
import StatusBadge from '../components/StatusBadge.vue';
import StatePanel from '../components/StatePanel.vue';
const sessions = ref<Session[]>([]);
const categories = ref<Category[]>([]);
const loading = ref(true);
const error = ref('');
const total = ref(0);
const advanced = ref(false);
const filters = reactive({
  eventName: '',
  categoryId: '',
  location: '',
  ticketTypeName: '',
  minPriceJiao: '',
  maxPriceJiao: '',
  startsFrom: '',
  startsTo: '',
  salesStatus: '',
  page: 1,
  pageSize: 6,
  sortBy: 'startsAt',
  sortDirection: 'asc',
});
const minPrice = ref('');
const maxPrice = ref('');
const sort = ref('startsAt-asc');
const calendarDate = ref(new Date());
const selectedDay = ref('');
const monthTitle = computed(() =>
  new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(calendarDate.value),
);
const calendar = computed(() => {
  const year = calendarDate.value.getFullYear(),
    month = calendarDate.value.getMonth();
  const first = new Date(year, month, 1);
  const offset = (first.getDay() + 6) % 7;
  const cellCount = Math.ceil((offset + new Date(year, month + 1, 0).getDate()) / 7) * 7;
  return Array.from({ length: cellCount }, (_, i) => {
    const d = new Date(year, month, i - offset + 1);
    return {
      day: d.getDate(),
      outside: d.getMonth() !== month,
      value: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`,
    };
  });
});
const monthEvents = ref<Session[]>([]);
const eventDays = computed(
  () =>
    new Set(
      monthEvents.value.map((s) =>
        new Intl.DateTimeFormat('en-CA', {
          timeZone: 'Asia/Shanghai',
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
        }).format(new Date(s.startsAt)),
      ),
    ),
);
const today = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).format(new Date());
async function load(reset = false) {
  if (reset) filters.page = 1;
  loading.value = true;
  error.value = '';
  try {
    filters.minPriceJiao =
      minPrice.value === '' ? '' : String(Math.round(Number(minPrice.value) * 10));
    filters.maxPriceJiao =
      maxPrice.value === '' ? '' : String(Math.round(Number(maxPrice.value) * 10));
    if (minPrice.value && maxPrice.value && Number(minPrice.value) > Number(maxPrice.value))
      throw new Error('最低价格不能大于最高价格');
    const [by, direction] = sort.value.split('-');
    filters.sortBy = by!;
    filters.sortDirection = direction!;
    const result = await get<Page<Session>>(`/public/sessions${query(filters)}`);
    sessions.value = result.items;
    total.value = result.total;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}
function selectCategory(id: string) {
  filters.categoryId = id;
  load(true);
}
function selectDay(day: string) {
  selectedDay.value = selectedDay.value === day ? '' : day;
  filters.startsFrom = selectedDay.value ? `${selectedDay.value}T00:00:00+08:00` : '';
  filters.startsTo = selectedDay.value ? `${selectedDay.value}T23:59:59+08:00` : '';
  load(true);
}
function scrollToResults() {
  document.getElementById('event-results')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
function shiftMonth(offset: number) {
  calendarDate.value = new Date(
    calendarDate.value.getFullYear(),
    calendarDate.value.getMonth() + offset,
    1,
  );
}
function clearFilters() {
  Object.assign(filters, {
    eventName: '',
    categoryId: '',
    location: '',
    ticketTypeName: '',
    startsFrom: '',
    startsTo: '',
    salesStatus: '',
  });
  minPrice.value = '';
  maxPrice.value = '';
  selectedDay.value = '';
  sort.value = 'startsAt-asc';
  load(true);
}
onMounted(async () => {
  await load();
  try {
    categories.value = await get<Category[]>('/public/categories');
    monthEvents.value = (await get<Page<Session>>('/public/sessions?pageSize=100')).items;
  } catch {
    /* The main result error remains actionable. */
  }
});
</script>
<template>
  <section class="hero">
    <div class="hero-copy">
      <p class="eyebrow">MAKE TIME FOR WHAT YOU LOVE</p>
      <h1>总有一场相聚，<br />让<em>平常的日子</em>闪光。</h1>
      <p class="hero-description">
        一场音乐会，一次新的尝试，或一段意料之外的相遇。<br />在南京，发现下一个值得期待的现场。
      </p>
      <div class="hero-bottom">
        <span class="hero-line" />把时间，留给喜欢的事<AppIcon name="Sparkles" :size="13" />
      </div>
    </div>
    <div class="hero-feature">
      <div class="feature-copy">
        <p class="eyebrow">A LITTLE INSPIRATION</p>
        <h3>趁秋意，去相聚。</h3>
        <p>音乐、艺术与生活的小小灵感</p>
        <button class="feature-link" @click="scrollToResults">
          看看最近的活动<AppIcon name="ArrowUpRight" :size="13" />
        </button>
      </div>
      <div class="hero-leaves"><i class="leaf" /><i class="leaf" /><i class="leaf" /></div>
    </div>
  </section>
  <form class="filter-panel" @submit.prevent="load(true)">
    <div class="filter-top">
      <div class="search-input">
        <AppIcon name="Search" :size="17" /><input
          v-model="filters.eventName"
          aria-label="搜索活动名称"
          placeholder="搜索活动名称，发现你的下一场期待"
        />
      </div>
      <button
        class="filter-expand"
        type="button"
        :aria-expanded="advanced"
        @click="advanced = !advanced"
      >
        <AppIcon name="SlidersHorizontal" :size="15" />筛选条件<AppIcon
          name="ChevronDown"
          :size="12"
        /></button
      ><button class="btn primary" type="submit">搜索活动</button>
    </div>
    <div class="category-bar">
      <span class="category-label">活动分类</span
      ><button
        type="button"
        class="category-chip"
        :class="{ active: !filters.categoryId }"
        @click="selectCategory('')"
      >
        全部活动</button
      ><button
        v-for="category in categories"
        :key="category.id"
        type="button"
        class="category-chip"
        :class="{ active: filters.categoryId === category.id }"
        @click="selectCategory(category.id)"
      >
        {{ category.name }}
      </button>
    </div>
    <div v-if="advanced" class="advanced-filters">
      <div class="form-field">
        <label for="filter-location">活动地点</label
        ><input id="filter-location" v-model="filters.location" placeholder="地点关键词" />
      </div>
      <div class="form-field">
        <label for="filter-type">票种名称</label
        ><input id="filter-type" v-model="filters.ticketTypeName" placeholder="例如：免费预约" />
      </div>
      <div class="form-field">
        <label for="filter-status">售票状态</label
        ><select id="filter-status" v-model="filters.salesStatus">
          <option value="">全部状态</option>
          <option value="ON_SALE">报名中</option>
          <option value="SOLD_OUT">可候补</option>
          <option value="NOT_OPEN">未开售</option>
          <option value="STOPPED">已停售</option>
          <option value="CLOSED">已截止</option>
        </select>
      </div>
      <div class="form-field">
        <label for="filter-min">最低价格（元）</label
        ><input
          id="filter-min"
          v-model="minPrice"
          type="number"
          min="0"
          step="0.1"
          placeholder="不限"
        />
      </div>
      <div class="form-field">
        <label for="filter-max">最高价格（元）</label
        ><input
          id="filter-max"
          v-model="maxPrice"
          type="number"
          min="0"
          step="0.1"
          placeholder="不限"
        />
      </div>
      <div class="form-field">
        <label>重置筛选</label
        ><button class="btn secondary" type="button" @click="clearFilters">
          <AppIcon name="RefreshCw" :size="14" />清除全部条件
        </button>
      </div>
    </div>
  </form>
  <div id="event-results" class="results-layout">
    <section>
      <div class="results-header">
        <h2>
          {{ selectedDay ? `${selectedDay.slice(5).replace('-', '月')}日的现场` : '发现喜欢的现场'
          }}<small>{{ total }} 场活动，等待相遇</small>
        </h2>
        <select v-model="sort" class="sort-select" aria-label="活动排序" @change="load(true)">
          <option value="startsAt-asc">即将开始优先</option>
          <option value="startsAt-desc">开始时间倒序</option>
          <option value="remaining-desc">剩余名额由多到少</option>
          <option value="remaining-asc">剩余名额由少到多</option>
        </select>
      </div>
      <StatePanel
        v-if="loading || error"
        :loading="loading"
        :error="error"
        @retry="load()"
      /><StatePanel
        v-else-if="!sessions.length"
        title="暂时没有匹配的活动"
        description="试试其他分类或日期，新的相聚正在路上。"
        ><button class="btn secondary" @click="clearFilters">查看全部活动</button></StatePanel
      >
      <div v-else class="event-grid">
        <article v-for="session in sessions" :key="session.id" class="event-card">
          <RouterLink :to="`/events/${session.eventId}`" class="card-art-link"
            ><StatusBadge :status="session.sessionSalesStatus" /><PosterArt
              :variant="categories.findIndex((c) => c.id === session.categoryId)"
              :title="session.eventName"
              :src="session.posterUrl"
              :category="session.categoryNameSnapshot"
          /></RouterLink>
          <div class="event-card-content">
            <h3>
              <RouterLink :to="`/events/${session.eventId}`">{{ session.eventName }}</RouterLink>
            </h3>
            <div class="event-meta">
              <AppIcon name="CalendarDays" :size="13" />{{
                date(session.startsAt, { weekday: 'short' })
              }}
              · {{ time(session.startsAt) }}—{{ time(session.endsAt) }}
            </div>
            <div class="event-meta"><AppIcon name="MapPin" :size="13" />{{ session.location }}</div>
            <div class="event-card-bottom">
              <span
                class="price"
                :class="{ free: Math.min(...session.ticketTypes.map((t) => t.priceJiao)) === 0 }"
                >{{
                  Math.min(...session.ticketTypes.map((t) => t.priceJiao)) === 0
                    ? '免费预约'
                    : `¥ ${money(Math.min(...session.ticketTypes.map((t) => t.priceJiao)))}`
                }}<small v-if="Math.min(...session.ticketTypes.map((t) => t.priceJiao)) > 0"
                  >起</small
                ></span
              ><RouterLink :to="`/events/${session.eventId}`" class="text-link"
                >查看活动<AppIcon name="ArrowUpRight" :size="13"
              /></RouterLink>
            </div>
          </div>
        </article>
      </div>
      <div v-if="total > 0" class="pagination">
        <span
          >第 {{ (filters.page - 1) * filters.pageSize + 1 }}—{{
            Math.min(filters.page * filters.pageSize, total)
          }}
          场 / 共 {{ total }} 场</span
        >
        <div class="page-buttons">
          <button
            :disabled="filters.page === 1 || loading"
            aria-label="上一页"
            @click="
              filters.page--;
              load();
            "
          >
            <AppIcon name="ChevronLeft" :size="14" /></button
          ><span class="current">{{ filters.page }}</span
          ><button
            :disabled="filters.page * filters.pageSize >= total || loading"
            aria-label="下一页"
            @click="
              filters.page++;
              load();
            "
          >
            <AppIcon name="ChevronRight" :size="14" />
          </button>
        </div>
      </div>
    </section>
    <aside class="discovery-aside">
      <section class="calendar-panel">
        <div class="calendar-heading">
          <strong>{{ monthTitle }}</strong>
          <div>
            <button class="icon-btn" aria-label="上个月" @click="shiftMonth(-1)">
              <AppIcon name="ChevronLeft" :size="13" /></button
            ><button class="icon-btn" aria-label="下个月" @click="shiftMonth(1)">
              <AppIcon name="ChevronRight" :size="13" />
            </button>
          </div>
        </div>
        <div class="calendar-grid">
          <span
            v-for="day in ['一', '二', '三', '四', '五', '六', '日']"
            :key="day"
            class="calendar-weekday"
            >{{ day }}</span
          ><button
            v-for="day in calendar"
            :key="day.value"
            class="calendar-day"
            :class="{
              outside: day.outside,
              selected: day.value === (selectedDay || today),
              'has-event': eventDays.has(day.value),
            }"
            :aria-label="`${day.value}${eventDays.has(day.value) ? '有活动' : ''}`"
            @click="selectDay(day.value)"
          >
            {{ day.day }}
          </button>
        </div>
        <div class="calendar-caption"><i />有活动的日子 · 点击日期筛选</div>
      </section>
      <section class="aside-note">
        <AppIcon name="Ticket" :size="23" />
        <h3>你的下一张入场券</h3>
        <p>报名成功后，在「我的订单」<br />找到专属于你的电子票。</p>
        <RouterLink to="/orders" class="text-link"
          >前往我的订单<AppIcon name="ArrowRight" :size="13"
        /></RouterLink>
      </section>
      <section class="aside-tips">
        <h4>让相聚更从容</h4>
        <div>
          <AppIcon name="UserRound" :size="14" />
          <p>每个账号每场限 1 张票，只能给本人报名，不能替他人报名或转赠。</p>
        </div>
        <div>
          <AppIcon name="Clock3" :size="14" />
          <p>提交报名后名额保留 15 分钟，请在这段时间内确认支付。</p>
        </div>
        <div>
          <AppIcon name="Leaf" :size="14" />
          <p>免费票也要走一次确认支付，完成后才会生成电子票。</p>
        </div>
      </section>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
const props = withDefaults(
  defineProps<{
    variant?: number;
    title?: string;
    category?: string;
    large?: boolean;
    src?: string | null;
  }>(),
  { variant: 0, title: '晚风来信', category: 'AUTUMN LIVE', large: false, src: null },
);
const index = computed(() => ((props.variant % 6) + 6) % 6);
const palettes = [
  ['#e7e0d5', '#c9a39b', '#9ba88f', '#748570', '#435a4b'],
  ['#e5e4d8', '#b6bb9c', '#c3a186', '#8d9f94', '#596c62'],
  ['#e7dcd5', '#bd8c83', '#c8ae99', '#91837b', '#5b5651'],
  ['#e0e5dd', '#b3bea6', '#c7b9a0', '#80918a', '#4c6860'],
  ['#e2e3d4', '#d2bc8e', '#9baa8c', '#748876', '#456357'],
  ['#e8ded3', '#c6a58a', '#bbaf94', '#a08e7e', '#6f6158'],
];
</script>
<template>
  <div
    class="poster-art"
    :class="{ large }"
    :style="{ background: palettes[index]![0], '--art-ink': palettes[index]![4] }"
  >
    <img v-if="src" :src="src" :alt="`${title}海报`" class="poster-uploaded-image" />
    <svg
      class="poster-illustration"
      viewBox="0 0 600 500"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
    >
      <circle cx="455" cy="137" r="66" :fill="palettes[index]![1]" />
      <template v-if="index === 1">
        <rect
          x="290"
          y="160"
          width="180"
          height="260"
          rx="90"
          :fill="palettes[1]![2]"
          transform="rotate(18 380 290)"
        />
        <rect
          x="350"
          y="200"
          width="115"
          height="200"
          rx="57"
          fill="#efece3"
          transform="rotate(-18 400 300)"
        />
        <path d="M0 420Q150 310 300 440T600 390V500H0Z" :fill="palettes[1]![3]" />
      </template>
      <template v-else-if="index === 2">
        <path d="M0 230Q90 90 180 240V500H0Z" :fill="palettes[2]![1]" opacity=".75" />
        <path d="M600 160Q470 40 390 250V500H600Z" :fill="palettes[2]![3]" opacity=".8" />
        <path d="M0 440Q300 390 600 440V500H0Z" :fill="palettes[2]![4]" />
        <path d="M267 430V237Q300 170 333 237V430" fill="#efe5d8" />
      </template>
      <template v-else-if="index === 5">
        <path d="M290 260H465L440 440Q380 468 315 440Z" :fill="palettes[5]![2]" />
        <ellipse cx="378" cy="260" rx="88" ry="23" :fill="palettes[5]![3]" />
        <path
          d="M465 285Q540 280 510 365Q490 395 450 380"
          fill="none"
          :stroke="palettes[5]![3]"
          stroke-width="20"
        />
        <path d="M0 460Q230 420 600 460V500H0Z" :fill="palettes[5]![4]" opacity=".35" />
      </template>
      <template v-else>
        <path d="M-60 355Q90 215 235 337T650 245V500H-60Z" :fill="palettes[index]![2]" />
        <path d="M-60 410Q110 285 288 393T650 335V500H-60Z" :fill="palettes[index]![3]" />
        <path
          d="M-60 478Q135 370 285 445T650 390V500H-60Z"
          :fill="palettes[index]![4]"
          opacity=".6"
        />
        <g stroke="#e7e1c8" stroke-width="2" fill="none" opacity=".9">
          <path
            d="M48 500L102 342M84 400L64 373M87 390L110 365M170 500L215 382M190 445L171 425M198 422L222 402M445 500Q425 435 462 386"
          />
        </g>
      </template>
      <path
        d="M34 33H566V467H34Z"
        stroke="currentColor"
        stroke-width=".5"
        fill="none"
        opacity=".15"
      />
    </svg>
    <div class="poster-copy">
      <span class="poster-kicker">{{ category }}</span>
      <h3>{{ title.split(' · ')[0] }}</h3>
      <span class="poster-line">把日常，过成值得期待的现场。</span>
    </div>
    <span class="poster-foot">NANJING · GOOD MOMENTS</span>
  </div>
</template>

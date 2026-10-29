<script setup>
import { ref, computed, nextTick, useAttrs, watch } from 'vue'
defineOptions({ inheritAttrs: false })
const props = defineProps({
  modelValue: { type: String, default: '' },
  type: { type: String, default: 'date' },
  label: { type: String, default: '日期' },
  min: String, max: String, required: Boolean, disabled: Boolean
})
const emit = defineEmits(['update:modelValue'])
const attrs = useAttrs(), dialog = ref(null), input = ref(null)
const year = ref(0), month = ref(0), selected = ref(''), time = ref('00:00')
const dateTime = computed(() => props.type === 'datetime-local')
const selectedHour = computed({ get: () => time.value.slice(0, 2), set: value => { time.value = value + ':' + time.value.slice(3, 5) } })
const selectedMinute = computed({ get: () => time.value.slice(3, 5), set: value => { time.value = time.value.slice(0, 2) + ':' + value } })
// datetime-local 的原生欄位會依作業系統顯示上午／下午，改用文字展示與自訂選時。
watch(() => [props.modelValue, props.min, props.max, props.type], async () => {
  await nextTick()
  const value = props.modelValue
  const invalid = dateTime.value && value && (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)
    || (props.min && value < props.min) || (props.max && value > props.max))
  input.value?.setCustomValidity(invalid ? '請選擇有效範圍內的日期與時間。' : '')
}, { immediate: true })
const pad = n => String(n).padStart(2, '0')
const dateText = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const years = computed(() => {
  const first = Math.min(1900, year.value), last = Math.max(new Date().getFullYear() + 20, year.value)
  return Array.from({ length: last - first + 1 }, (_, i) => first + i)
})
const days = computed(() => {
  const first = new Date(year.value, month.value, 1), start = 1 - first.getDay()
  return Array.from({ length: 42 }, (_, i) => {
    const d = new Date(year.value, month.value, start + i), value = dateText(d)
    return {
      value, day: d.getDate(), outside: d.getMonth() !== month.value,
      today: value === dateText(new Date()), disabled: !allowed(value)
    }
  })
})
function allowed(value) {
  return (!props.min || value >= props.min.slice(0, 10)) && (!props.max || value <= props.max.slice(0, 10))
}
const candidate = computed(() => selected.value && (dateTime.value ? `${selected.value}T${time.value}` : selected.value))
const canApply = computed(() => candidate.value && (!dateTime.value || /^\d{2}:\d{2}$/.test(time.value))
  && (!props.min || candidate.value >= props.min) && (!props.max || candidate.value <= props.max))
async function open() {
  if (props.disabled || dialog.value?.open) return
  const value = props.modelValue || dateText(new Date())
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  const d = match ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : new Date()
  year.value = d.getFullYear(); month.value = d.getMonth()
  selected.value = props.modelValue?.slice(0, 10) || ''
  time.value = props.modelValue?.slice(11, 16) || '00:00'
  dialog.value.showModal()
  await nextTick()
  dialog.value.querySelector('[aria-pressed="true"]:not(:disabled), .calendar-day:not(:disabled)')?.focus()
}
function close() { dialog.value?.close(); input.value?.focus() }
function move(offset) {
  const d = new Date(year.value, month.value + offset, 1)
  year.value = d.getFullYear(); month.value = d.getMonth()
}
function choose(value) {
  if (!allowed(value)) return
  selected.value = value
  if (!dateTime.value) apply()
}
function apply() {
  if (!canApply.value || props.disabled) return
  emit('update:modelValue', candidate.value); close()
}
function clear() { if (!props.disabled) { emit('update:modelValue', ''); close() } }
function today() {
  const d = new Date(), value = dateText(d)
  year.value = d.getFullYear(); month.value = d.getMonth()
  if (allowed(value)) choose(value)
}
function escape(event) { event.preventDefault(); close() }
</script>

<template>
  <span class="hero-date-picker">
    <span class="date-field">
      <input ref="input" v-bind="attrs" class="date-value" :type="dateTime ? 'text' : type" :value="dateTime ? modelValue.replace('T', ' ') : modelValue" :aria-readonly="dateTime || undefined"
        :aria-label="attrs['aria-label'] || label" :required="required" :disabled="disabled" :min="min" :max="max"
        @beforeinput="dateTime && $event.preventDefault()" @paste="dateTime && $event.preventDefault()"
        @input="!dateTime && emit('update:modelValue', $event.target.value)" @click.prevent="open" @keydown.alt.down.prevent="open">
      <button type="button" class="calendar-trigger" :disabled="disabled" :aria-label="`開啟${label}日曆`"
        aria-haspopup="dialog" @click.prevent="open">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <rect x="3" y="5" width="18" height="16" rx="3" />
          <path d="M7 2v6m10-6v6M3 11h18M7 15h2m3 0h2m3 0h1M7 18h2m3 0h2" />
        </svg>
      </button>
    </span>
    <dialog ref="dialog" class="hero-calendar" :aria-label="`選擇${label}`" @cancel="escape" @click.stop>
      <div class="calendar-heading"><strong>選擇{{ label }}</strong><button type="button" class="calendar-close" aria-label="關閉日曆" title="關閉日曆"
          @click="close"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg></button></div>
      <div class="calendar-navigation">
        <button type="button" aria-label="上個月" @click="move(-1)">‹</button>
        <select v-model.number="year" aria-label="年份">
          <option v-for="y in years" :key="y" :value="y">{{ y }} 年</option>
        </select>
        <select v-model.number="month" aria-label="月份">
          <option v-for="m in 12" :key="m" :value="m - 1">{{ m }} 月</option>
        </select>
        <button type="button" aria-label="下個月" @click="move(1)">›</button>
      </div>
      <div class="calendar-week" aria-hidden="true"><span v-for="day in ['日', '一', '二', '三', '四', '五', '六']"
          :key="day">{{ day
          }}</span></div>
      <div class="calendar-days">
        <button v-for="day in days" :key="day.value" type="button" class="calendar-day"
          :class="{ outside: day.outside, today: day.today }" :disabled="day.disabled" :aria-label="day.value"
          :aria-pressed="day.value === selected" :aria-current="day.today ? 'date' : undefined"
          @click="choose(day.value)">{{ day.day }}</button>
      </div>
      <div v-if="dateTime" class="calendar-time"><span>時間<br><small>24 小時制</small></span>
        <div class="calendar-time-parts"><label><select v-model="selectedHour" aria-label="小時（24小時制）"><option v-for="hour in 24" :key="hour" :value="pad(hour - 1)">{{ pad(hour - 1) }}</option></select><small>時</small></label><span aria-hidden="true">:</span><label><select v-model="selectedMinute" aria-label="分鐘"><option v-for="minute in 60" :key="minute" :value="pad(minute - 1)">{{ pad(minute - 1) }}</option></select><small>分</small></label></div>
      </div>
      <div class="calendar-footer">
        <button type="button" class="calendar-text-button" @click="today">今天</button>
        <button v-if="!required" type="button" class="calendar-text-button" @click="clear">清除</button>
        <button type="button" class="calendar-text-button" @click="close">取消</button>
        <button v-if="dateTime" type="button" class="calendar-confirm" :disabled="!canApply" @click="apply">確定</button>
      </div>
    </dialog>
  </span>
</template>

<style scoped>
.hero-calendar .calendar-heading .calendar-close { display: grid; place-items: center; width: 36px; height: 36px; flex: none; padding: 8px; margin: 0; border: 1px solid #526b9f; border-radius: 11px; background: #223665; color: #f2f5ff; box-shadow: 0 3px 9px #06112d22; transition: background .15s, border-color .15s; }
.hero-calendar .calendar-heading .calendar-close:hover { background: #36518a; border-color: #89a9dc; }
.hero-calendar .calendar-heading .calendar-close:focus-visible { outline: 3px solid #d4a64a; outline-offset: 3px; }
.hero-calendar .calendar-close svg { display: block; width: 18px; height: 18px; color: inherit; }
.hero-calendar .calendar-time { justify-content: space-between; }
.hero-calendar .calendar-time small { font-size: 11px; opacity: .8; white-space: nowrap; }
.hero-calendar .calendar-time-parts { display: flex; align-items: center; gap: 9px; min-width: 0; }
.hero-calendar .calendar-time-parts label { display: flex; align-items: center; gap: 5px; margin: 0; }
.hero-calendar .calendar-time-parts select { box-sizing: border-box; width: 66px; min-width: 0; height: 40px; margin: 0; padding: 6px 9px; border: 1px solid #829bbb; border-radius: 8px; background: #edf3fc; color: #203c5d; font: inherit; font-variant-numeric: tabular-nums; }

.hero-date-picker {
  display: block;
  width: 100%;
  min-width: 0;
}

.date-field {
  display: flex;
  position: relative;
  width: 100%;
}

.hero-date-picker .date-value {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding-right: 50px;
}

.date-value::-webkit-calendar-picker-indicator {
  display: none;
}

.hero-date-picker .calendar-trigger {
  display: grid;
  place-items: center;
  position: absolute;
  right: 5px;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  height: 34px;
  min-height: 0;
  padding: 7px;
  margin: 0;
  border: 0;
  border-radius: 7px;
  color: #17647d;
  background: #e8f3f8;
  cursor: pointer;
}

.calendar-trigger svg {
  width: 21px;
  height: 21px;
}

.hero-calendar {
  position: fixed;
  inset: 0;
  box-sizing: border-box;
  width: min(360px, calc(100vw - 24px));
  max-height: calc(100dvh - 24px);
  overflow: auto;
  margin: auto;
  padding: 20px;
  background: #fff;
  color: #234456;
  border: 1px solid #bad4e2;
  border-radius: 18px;
  box-shadow: 0 20px 70px #102b4f55;
  font: 14px/1.5 system-ui, 'Microsoft JhengHei', sans-serif;
}

.hero-calendar::backdrop {
  background: #102b4f66;
}

.hero-calendar button {
  box-sizing: border-box;
  font: inherit;
  cursor: pointer;
}

.calendar-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.hero-calendar .calendar-heading button {
  border: 0;
  background: #edf4f8;
  color: #34576b;
  border-radius: 8px;
  width: 32px;
  height: 32px;
  padding: 0;
  margin: 0;
}

.calendar-navigation {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.hero-calendar .calendar-navigation button {
  flex: 0 0 30px;
  border: 0;
  background: #edf4f8;
  color: #17647d;
  border-radius: 8px;
  height: 36px;
  padding: 0;
  margin: 0;
  font-size: 24px;
}

.hero-calendar .calendar-navigation select {
  width: auto;
  min-width: 0;
  flex: 1;
  height: 36px;
  margin: 0;
  padding: 0 5px;
  background: #f7fbfd;
  color: #234456;
  border: 1px solid #cadfe8;
  border-radius: 7px;
  font: inherit;
}

.calendar-week,
.calendar-days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 3px;
  text-align: center;
}

.calendar-week {
  color: #6d8795;
  font-size: 12px;
  padding: 6px 0;
}

.hero-calendar .calendar-day {
  border: 1px solid transparent;
  background: transparent;
  color: #234456;
  border-radius: 8px;
  min-height: 36px;
  padding: 6px 0;
  margin: 0;
  font-size: 13px;
}

.hero-calendar .calendar-day.outside {
  color: #8298a5;
}

.hero-calendar .calendar-day.today {
  border-color: #27869d;
}

.hero-calendar .calendar-day:hover:not(:disabled) {
  background: #e5f4f8;
}

.hero-calendar .calendar-day[aria-pressed='true'] {
  background: #176e89;
  color: #fff;
}

.hero-calendar .calendar-day[aria-pressed='true']:hover:not(:disabled) {
  background: #125a70;
  color: #fff;
}

.hero-calendar button:disabled {
  opacity: .35;
  cursor: not-allowed;
}

.hero-calendar button:focus-visible,
.hero-calendar select:focus-visible {
  outline: 2px solid #16839c;
  outline-offset: 2px;
}

.hero-calendar .calendar-time {
  display: flex;
  flex-direction: row;
  gap: 12px;
  align-items: center;
  margin: 18px 0 0;
  color: #34576b;
  font-size: 13px;
}

.hero-calendar .calendar-time input {
  flex: 1;
  width: auto;
  min-width: 0;
  margin: 0;
  padding: 8px 10px;
  border: 1px solid #cadfe8;
  border-radius: 8px;
  color: #234456;
  background: #f7fbfd;
}

.calendar-footer {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 18px;
}

.hero-calendar .calendar-text-button {
  background: #edf4f8;
  color: #17647d;
  border: 0;
  border-radius: 7px;
  padding: 8px 10px;
  margin: 0;
  font-size: 13px;
}

.hero-calendar .calendar-confirm {
  background: #176e89;
  color: #fff;
  border: 0;
  border-radius: 7px;
  padding: 8px 14px;
  margin: 0;
  font-size: 13px;
}
</style>

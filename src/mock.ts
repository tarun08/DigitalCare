// Browser-only stand-in for the Android plugin: `npm run dev` shows the real UI with fake data.
// Preview the other screens with ?screen=short, ?screen=long, ?screen=water or ?screen=phone. ?overlay=on: "Display over other apps" is allowed.
import type { DigitalCarePlugin, Screen, Settings, State } from './plugin'

const MIN = 60_000
const t0 = Date.now()
const tomorrow = t0 + 12 * 60 * MIN
const screen = (new URLSearchParams(location.search).get('screen') ?? 'home') as Screen
// ?water=none: no reminder left, cutoff ahead. ?water=late: no reminder left, cutoff passed.
const water = new URLSearchParams(location.search).get('water')
// ?phone=off: screen off. ?phone=stopped: the phone-use service is down. ?phone=reminded: a reminder went out 5 min ago. ?phone=fresh: the stretch began 20 s ago.
const phone = new URLSearchParams(location.search).get('phone')
const at = (h: number, m = 0) => new Date().setHours(h, m, 0, 0) // today at h:m
const waterCutoff = water === 'late' ? t0 - MIN : water === 'none' ? t0 + 40 * MIN : at(21, 30)
const nextWater = t0 + 40 * MIN
const settings: Settings = {
  dayWake: [420, 420, 420, 420, 420, 510, 510], daySleep: [1380, 1380, 1380, 1380, 1380, 1410, 1410], dayOn: [1, 1, 1, 1, 1, 1, 1],
  breaksOn: true, shortEveryMin: 20, shortSec: 20, longEveryMin: 60, longMin: 5,
  waterOn: true, smartWater: true, customGoal: 8, customEveryMin: 60, glassMl: 250, stopBeforeSleepMin: 90, wakeScreen: false, screenOn: true, screenLimitMin: 15, waterCustom: false, waterStart: '09:00', waterStop: '21:00',
  shortSound: 0, longSound: 0, overSound: 2, waterSound: 0, phoneSound: 0,
  shortPopup: true, longPopup: true, overPopup: false, waterPopup: true, phonePopup: true,
  shortFull: false, longFull: false, waterFull: false, phoneFull: false,
  language: new URLSearchParams(location.search).get('lang') === 'hi' ? 'hi' : 'en', // ?lang=hi previews Hindi
}
let glasses = 3
let pausedUntil: number | null = null

const state = (): State => {
  const paused = pausedUntil !== null && pausedUntil > Date.now()
  return {
    settings: { ...settings }, glasses, goal: 10, season: 'winter',
    tip: settings.language === 'hi' ? 'नकली डेटा: आप ब्राउज़र पूर्वावलोकन में हैं।' : 'Fake data: you are in the browser preview.',
    inDay: true, nextDayStartAt: tomorrow,
    nextBreakAt: paused ? null : t0 + 14 * MIN, nextBreakType: paused ? null : 'short',
    breakEndAt: screen === 'long' ? t0 + 5 * MIN : screen === 'short' ? t0 + 20_000 : null,
    // like the real rule: a reminder that would land after the stop time is dropped
    nextWaterAt: paused || water === 'none' || water === 'late' || nextWater > waterCutoff ? null : nextWater,
    waterCutoffAt: waterCutoff,
    waterStartAt: at(7, 10), dayStartAt: at(7), dayEndAt: at(23),
    pausedUntil: paused ? pausedUntil : null,
    phoneRunning: phone !== 'stopped',
    phoneInUseSince: phone === 'off' || phone === 'stopped' ? null : t0 - (phone === 'fresh' ? 20_000 : 7 * MIN),
    phoneRemindedAt: phone === 'reminded' ? t0 - 5 * MIN : null,
    setup: { notifications: true, battery: false, fullScreen: true, overlay: new URLSearchParams(location.search).get('overlay') === 'on', phoneUse: phone !== 'stopped' },
    manufacturer: 'vivo',
  }
}

const same = async () => state()

export const mock: DigitalCarePlugin = {
  getState: same,
  saveSettings: async s => { Object.assign(settings, s); return state() },
  logGlass: async () => { glasses++; return state() },
  removeGlass: async () => { glasses = Math.max(0, glasses - 1); return state() },
  pause: async ({ minutes }) => { pausedUntil = minutes === null ? tomorrow : Date.now() + minutes * MIN; return state() },
  resume: async () => { pausedUntil = null; return state() },
  skipBreak: same,
  snoozeBreak: same,
  testAlert: async o => { (window as unknown as { __lastTest?: unknown }).__lastTest = o }, // read by the browser checks
  openSystemSettings: async opts => { console.log('openSystemSettings', opts) },
  leave: async () => { console.log('leave') },
  getLaunchScreen: async () => ({ screen }),
  // The browser checks play a native event with window.__emit('launchScreen', { screen: 'short' }).
  addListener: (async (event: string, cb: (e?: unknown) => void) => {
    (listeners[event] ??= []).push(cb)
    return { remove: async () => { listeners[event] = listeners[event].filter(x => x !== cb) } }
  }) as DigitalCarePlugin['addListener'],
}

const listeners: Record<string, Array<(e?: unknown) => void>> = {}
;(window as unknown as { __emit: (event: string, payload?: unknown) => void }).__emit = (event, payload) =>
  listeners[event]?.forEach(cb => cb(payload))

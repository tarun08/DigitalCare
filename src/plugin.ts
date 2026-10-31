import { registerPlugin, type PluginListenerHandle } from '@capacitor/core'

export type Screen = 'home' | 'short' | 'long' | 'water'
export type SystemPage = 'channel' | 'battery' | 'fullScreen' | 'nightLight' | 'extraDim' | 'appDetails'
export type AlertId = 'short' | 'long' | 'over' | 'water' | 'phone'

export interface Settings {
  // Hours for each day, Monday first, as minutes after midnight. dayOn: 1 = reminders that day, 0 = none.
  dayWake: number[]; daySleep: number[]; dayOn: number[]
  breaksOn: boolean; shortEveryMin: number; shortSec: number; longEveryN: number; longMin: number
  waterOn: boolean; smartWater: boolean; customGoal: number; customEveryMin: number; glassMl: number
  stopBeforeSleepMin: number; wakeScreen: boolean
  screenOn: boolean; screenLimitMin: number
  waterCustom: boolean; waterStart: string; waterStop: string
  // Sound: 0 sound + vibration, 1 sound only, 2 vibration only, 3 silent. Popup false = quiet.
  shortSound: number; longSound: number; overSound: number; waterSound: number; phoneSound: number
  shortPopup: boolean; longPopup: boolean; overPopup: boolean; waterPopup: boolean; phonePopup: boolean
}

export interface State {
  settings: Settings
  glasses: number; goal: number; season: 'summer' | 'monsoon' | 'transition' | 'winter'; tip: string
  inDay: boolean; nextDayStartAt: number | null
  nextBreakAt: number | null; nextBreakType: 'short' | 'long' | null; breakEndAt: number | null
  nextWaterAt: number | null; waterCutoffAt: number | null; pausedUntil: number | null
  phoneRunning: boolean; phoneInUseSince: number | null; phoneRemindedAt: number | null
  dayStartAt: number | null; dayEndAt: number | null; waterStartAt: number | null
  setup: { notifications: boolean; battery: boolean; fullScreen: boolean; phoneUse: boolean }
  manufacturer: string
}

export interface DigitalCarePlugin {
  getState(): Promise<State>
  saveSettings(settings: Settings): Promise<State>
  logGlass(): Promise<State>
  removeGlass(): Promise<State>
  pause(opts: { minutes: number | null }): Promise<State>
  resume(): Promise<State>
  skipBreak(): Promise<State>
  snoozeBreak(): Promise<State>
  testAlert(opts: { alert: AlertId; sound: number; popup: boolean }): Promise<void>
  openSystemSettings(opts: { page: SystemPage; channel?: string }): Promise<void>
  getLaunchScreen(): Promise<{ screen: Screen }>
  addListener(event: 'launchScreen', cb: (e: { screen: Screen }) => void): Promise<PluginListenerHandle>
  addListener(event: 'appResume', cb: () => void): Promise<PluginListenerHandle>
}

// The browser has no Android plugin: the dev server gets a fake, and production builds drop it.
export const DigitalCare = registerPlugin<DigitalCarePlugin>('DigitalCare',
  import.meta.env.DEV ? { web: () => import('./mock').then(m => m.mock) } : undefined)

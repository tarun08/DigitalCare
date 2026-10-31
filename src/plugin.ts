import { registerPlugin, type PluginListenerHandle } from '@capacitor/core'

export type Screen = 'home' | 'short' | 'long' | 'water'
export type SystemPage = 'channel' | 'battery' | 'fullScreen' | 'nightLight' | 'extraDim' | 'appDetails'

export interface Settings {
  weekdayWake: string; weekdaySleep: string; weekendWake: string; weekendSleep: string; weekendDays: number[]
  breaksOn: boolean; shortEveryMin: number; shortSec: number; longEveryN: number; longMin: number
  waterOn: boolean; smartWater: boolean; customGoal: number; customEveryMin: number; glassMl: number
  stopBeforeSleepMin: number; wakeScreen: boolean
}

export interface State {
  settings: Settings
  glasses: number; goal: number; season: 'summer' | 'monsoon' | 'transition' | 'winter'; tip: string
  inDay: boolean; nextDayStartAt: number | null
  nextBreakAt: number | null; nextBreakType: 'short' | 'long' | null; breakEndAt: number | null
  nextWaterAt: number | null; pausedUntil: number | null
  setup: { notifications: boolean; battery: boolean; fullScreen: boolean }
  manufacturer: string
}

export interface EyeCarePlugin {
  getState(): Promise<State>
  saveSettings(settings: Settings): Promise<State>
  logGlass(): Promise<State>
  pause(opts: { minutes: number | null }): Promise<State>
  resume(): Promise<State>
  skipBreak(): Promise<State>
  snoozeBreak(): Promise<State>
  openSystemSettings(opts: { page: SystemPage; channel?: string }): Promise<void>
  getLaunchScreen(): Promise<{ screen: Screen }>
  addListener(event: 'launchScreen', cb: (e: { screen: Screen }) => void): Promise<PluginListenerHandle>
  addListener(event: 'appResume', cb: () => void): Promise<PluginListenerHandle>
}

export const EyeCare = registerPlugin<EyeCarePlugin>('EyeCare')

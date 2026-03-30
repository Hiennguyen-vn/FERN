import { create } from 'zustand'

interface NavigationState {
  currentPath: string
  setCurrentPath: (value: string) => void
}

export const useNavigationStore = create<NavigationState>((set) => ({
  currentPath: '/home',
  setCurrentPath: (value) => set({ currentPath: value }),
}))

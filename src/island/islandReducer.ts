import {
  AppTransitionDirection,
  IslandDisplayMode,
  IslandEvent,
  IslandSettings,
  IslandState,
  MusicTrack,
  createDefaultSettings,
} from './types';

export function createInitialIslandState(): IslandState {
  return {
    activeEvent: null,
    queue: [],
    displayMode: 'IDLE',
    persistentMusic: null,
    isFullscreen: false,
    callStatusMessage: null,
    activeTransition: null,
  };
}

export function clampSettings(patch: Partial<IslandSettings>, current: IslandSettings): IslandSettings {
  const next: IslandSettings = { ...current, ...patch };
  const rawSpeed = Number.isFinite(next.animationSpeed) ? next.animationSpeed : 1.0;
  return {
    ...next,
    offsetX: Math.max(-80, Math.min(80, Math.round(next.offsetX))),
    offsetY: Math.max(4, Math.min(64, Math.round(next.offsetY))),
    idleWidth: Math.max(84, Math.min(200, Math.round(next.idleWidth))),
    idleHeight: Math.max(24, Math.min(50, Math.round(next.idleHeight))),
    cornerRadius: Math.max(10, Math.min(32, Math.round(next.cornerRadius))),
    animationSpeed: Math.max(0.5, Math.min(2.0, Math.round(rawSpeed * 20) / 20)),
  };
}

export function resetPositionAndSize(current: IslandSettings): IslandSettings {
  const defaults = createDefaultSettings();
  return {
    ...current,
    offsetX: defaults.offsetX,
    offsetY: defaults.offsetY,
    idleWidth: defaults.idleWidth,
    idleHeight: defaults.idleHeight,
    cornerRadius: defaults.cornerRadius,
  };
}

export function isStickyEvent(event: IslandEvent): boolean {
  if (event.type === 'INCOMING_CALL') return true;
  if (event.type === 'MUSIC_PLAYER') return event.track.isPlaying;
  return false;
}

function handleMusicSessionEnded(state: IslandState): IslandState {
  if (state.activeEvent?.type === 'MUSIC_PLAYER') {
    return advanceOrCollapseIsland({
      ...state,
      persistentMusic: null,
    });
  }
  return {
    ...state,
    persistentMusic: null,
  };
}

function resolveExpandableCandidate(state: IslandState): IslandEvent | null {
  const candidate: IslandEvent | null =
    state.activeEvent ??
    (state.persistentMusic
      ? { id: 'music_session', type: 'MUSIC_PLAYER', track: state.persistentMusic }
      : null);

  if (!candidate) return null;
  if (candidate.type === 'MUSIC_SESSION_ENDED') return null;
  if (candidate.type === 'MUSIC_PLAYER' && candidate.track.title.trim().length === 0) {
    return null;
  }
  return candidate;
}

export function enqueueIslandEvent(
  state: IslandState,
  event: IslandEvent,
  settings: IslandSettings
): IslandState {
  if (!settings.masterEnabled) {
    return state;
  }

  if (event.type === 'MUSIC_SESSION_ENDED') {
    return handleMusicSessionEnded(state);
  }

  if (!settings.enabledEvents[event.type]) {
    return state;
  }

  if (event.type === 'BLUETOOTH' && settings.bluetoothAudioOnly && !event.isAudio) {
    return state;
  }

  if (event.type === 'MUSIC_PLAYER') {
    const track = event.track;
    if (track.title.trim().length === 0) {
      return handleMusicSessionEnded(state);
    }
    const shouldPromote = !state.activeEvent || state.activeEvent.type === 'MUSIC_PLAYER';
    if (!shouldPromote) {
      return { ...state, persistentMusic: track };
    }
    const isExpanded =
      state.displayMode === 'EXPANDED' || state.displayMode === 'FULL_EXPANDED';
    const targetMode: IslandDisplayMode = isExpanded
      ? state.displayMode
      : track.isPlaying
      ? 'COMPACT'
      : 'IDLE';

    return {
      ...state,
      persistentMusic: track,
      activeEvent: targetMode === 'IDLE' ? null : event,
      displayMode: targetMode,
    };
  }

  if (event.type === 'INCOMING_CALL') {
    const filteredQueue = state.queue.filter((item) => item.type !== 'INCOMING_CALL');
    return {
      ...state,
      activeEvent: event,
      queue: filteredQueue,
      displayMode: 'EXPANDED',
      callStatusMessage: null,
    };
  }

  if (!state.activeEvent || state.activeEvent.type === 'MUSIC_PLAYER') {
    return {
      ...state,
      activeEvent: event,
      displayMode: 'COMPACT',
    };
  }

  const existingIndex = state.queue.findIndex((item) => item.type === event.type);
  if (existingIndex >= 0) {
    const updatedQueue = [...state.queue];
    updatedQueue[existingIndex] = event;
    return {
      ...state,
      queue: updatedQueue,
    };
  }

  const nextQueue = state.queue.length >= 4 ? state.queue.slice(1) : state.queue;
  return {
    ...state,
    queue: [...nextQueue, event],
  };
}

export function advanceOrCollapseIsland(state: IslandState): IslandState {
  if (state.queue.length > 0) {
    const [next, ...rest] = state.queue;
    return {
      ...state,
      activeEvent: next,
      queue: rest,
      displayMode: next.type === 'INCOMING_CALL' ? 'EXPANDED' : 'COMPACT',
    };
  }

  if (state.persistentMusic && state.persistentMusic.isPlaying) {
    return {
      ...state,
      activeEvent: {
        id: 'music_session',
        type: 'MUSIC_PLAYER',
        track: state.persistentMusic,
      },
      queue: [],
      displayMode: 'COMPACT',
    };
  }

  return {
    ...state,
    activeEvent: null,
    queue: [],
    displayMode: 'IDLE',
  };
}

export function tapIsland(state: IslandState): IslandState {
  const candidate = resolveExpandableCandidate(state);

  if (state.displayMode === 'IDLE') {
    if (!candidate) return state;
    return { ...state, activeEvent: candidate, displayMode: 'EXPANDED' };
  }

  if (state.displayMode === 'COMPACT') {
    if (!candidate) return collapseIsland(state);
    return { ...state, activeEvent: candidate, displayMode: 'EXPANDED' };
  }

  return collapseIsland(state);
}

export function longPressIsland(state: IslandState): IslandState {
  const candidate = resolveExpandableCandidate(state);

  if (!candidate) return state;
  return {
    ...state,
    activeEvent: candidate,
    displayMode: 'FULL_EXPANDED',
  };
}

export function collapseIsland(state: IslandState): IslandState {
  if (state.activeEvent?.type === 'INCOMING_CALL') {
    return { ...state, displayMode: 'COMPACT' };
  }
  if (state.persistentMusic && state.persistentMusic.isPlaying) {
    return {
      ...state,
      activeEvent: {
        id: 'music_session',
        type: 'MUSIC_PLAYER',
        track: state.persistentMusic,
      },
      displayMode: 'COMPACT',
    };
  }
  return {
    ...state,
    activeEvent: null,
    displayMode: 'IDLE',
  };
}

export function resolveIncomingCall(state: IslandState, action: 'ACCEPT' | 'DECLINE'): IslandState {
  if (state.activeEvent?.type !== 'INCOMING_CALL') return state;
  const caller = state.activeEvent.callerName;
  const message = action === 'ACCEPT' ? `Connected with ${caller}` : `Declined call from ${caller}`;
  const next = advanceOrCollapseIsland({ ...state, activeEvent: null });
  return {
    ...next,
    callStatusMessage: message,
  };
}

export function toggleMusicPlayPause(state: IslandState): IslandState {
  if (!state.persistentMusic) return state;
  const updatedTrack: MusicTrack = {
    ...state.persistentMusic,
    isPlaying: !state.persistentMusic.isPlaying,
  };
  const updatedEvent: IslandEvent = {
    id: 'music_session',
    type: 'MUSIC_PLAYER',
    track: updatedTrack,
  };
  return {
    ...state,
    persistentMusic: updatedTrack,
    activeEvent:
      state.activeEvent?.type === 'MUSIC_PLAYER' ? updatedEvent : state.activeEvent,
  };
}

export function seekMusic(state: IslandState, positionSec: number): IslandState {
  if (!state.persistentMusic) return state;
  const clamped = Math.max(0, Math.min(state.persistentMusic.durationSec, Math.round(positionSec)));
  const updatedTrack: MusicTrack = {
    ...state.persistentMusic,
    positionSec: clamped,
  };
  const updatedEvent: IslandEvent = {
    id: 'music_session',
    type: 'MUSIC_PLAYER',
    track: updatedTrack,
  };
  return {
    ...state,
    persistentMusic: updatedTrack,
    activeEvent:
      state.activeEvent?.type === 'MUSIC_PLAYER' ? updatedEvent : state.activeEvent,
  };
}

export function startAppTransition(
  state: IslandState,
  direction: AppTransitionDirection,
  settings: IslandSettings,
  transitionId: string,
  dominantColor = '#312E81'
): IslandState {
  if (!settings.masterEnabled || !settings.appExitAnimationEnabled || !settings.enabledEvents.MUSIC_PLAYER) {
    return state;
  }
  const music = state.persistentMusic;
  if (!music) return state;

  const transition = {
    id: transitionId,
    direction,
    title: music.title,
    artist: music.artist,
    dominantColor,
  };

  if (direction === 'ENTER_FROM_ISLAND') {
    return {
      ...state,
      activeEvent: { id: 'music_session', type: 'MUSIC_PLAYER', track: music },
      displayMode: 'COMPACT',
      activeTransition: transition,
    };
  }

  return {
    ...state,
    activeTransition: transition,
  };
}

export function onExitAnimationReachedIsland(state: IslandState): IslandState {
  const music = state.persistentMusic;
  if (!music) return state;
  return {
    ...state,
    activeEvent: { id: 'music_session', type: 'MUSIC_PLAYER', track: music },
    displayMode: 'EXPANDED',
  };
}

export function finishAppTransition(state: IslandState, transitionId: string): IslandState {
  if (state.activeTransition?.id !== transitionId) return state;
  return {
    ...state,
    activeTransition: null,
  };
}

export function getIslandVerticalOffset(
  mode: IslandDisplayMode,
  activeEvent: IslandEvent | null,
  settings: IslandSettings,
  statusBarClearance = 42
): number {
  const resolvedMode: IslandDisplayMode = activeEvent ? mode : 'IDLE';
  if (resolvedMode === 'EXPANDED' || resolvedMode === 'FULL_EXPANDED') {
    return Math.max(settings.offsetY, statusBarClearance);
  }
  return settings.offsetY;
}

export function getIslandDimensions(
  mode: IslandDisplayMode,
  activeEvent: IslandEvent | null,
  settings: IslandSettings
): { width: number; height: number; radius: number } {
  const resolvedMode: IslandDisplayMode = activeEvent ? mode : 'IDLE';
  if (resolvedMode === 'IDLE') {
    return {
      width: settings.idleWidth,
      height: settings.idleHeight,
      radius: settings.cornerRadius,
    };
  }
  if (resolvedMode === 'COMPACT') {
    // Restrict compact width proportionally (<= 212px) so left/right status bar notifications remain clear
    const maxCompactW = 212;
    let desiredW = Math.max(settings.idleWidth + 68, 198);
    if (activeEvent?.type === 'MUSIC_PLAYER') desiredW = Math.max(settings.idleWidth + 64, 194);
    if (activeEvent?.type === 'INCOMING_CALL') desiredW = Math.max(settings.idleWidth + 76, 210);
    if (activeEvent?.type === 'BLUETOOTH') desiredW = Math.max(settings.idleWidth + 74, 208);
    if (activeEvent?.type === 'WIFI_CONNECTED') desiredW = Math.max(settings.idleWidth + 72, 204);
    return {
      width: Math.min(maxCompactW, desiredW),
      height: Math.min(38, Math.max(settings.idleHeight, 34)),
      radius: 20,
    };
  }
  if (resolvedMode === 'EXPANDED') {
    let height = 74;
    if (activeEvent?.type === 'INCOMING_CALL') height = 94;
    if (activeEvent?.type === 'MUSIC_PLAYER') height = 152;
    const width = Math.min(308, Math.max(276, Math.round(settings.idleWidth * 2.2)));
    return {
      width,
      height,
      radius: 26,
    };
  }
  let height = 98;
  if (activeEvent?.type === 'INCOMING_CALL') height = 116;
  if (activeEvent?.type === 'MUSIC_PLAYER') height = 176;
  const width = Math.min(322, Math.max(292, Math.round(settings.idleWidth * 2.35)));
  return {
    width,
    height,
    radius: 30,
  };
}

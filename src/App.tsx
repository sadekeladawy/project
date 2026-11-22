import { Suspense, lazy, useCallback, useEffect, useRef, useState } from 'react';
import { Download } from 'lucide-react';
import { EventTriggerPanel } from './components/EventTriggerPanel';
import { PhoneSimulator } from './components/PhoneSimulator';
import { SettingsPanel } from './components/SettingsPanel';

const AndroidCodeExplorer = lazy(() =>
  import('./components/AndroidCodeExplorer').then((mod) => ({
    default: mod.AndroidCodeExplorer,
  }))
);
import {
  advanceOrCollapseIsland,
  clampSettings,
  collapseIsland,
  createInitialIslandState,
  enqueueIslandEvent,
  finishAppTransition,
  isStickyEvent,
  longPressIsland,
  onExitAnimationReachedIsland,
  resetPositionAndSize,
  resolveIncomingCall,
  seekMusic,
  startAppTransition,
  tapIsland,
  toggleMusicPlayPause,
} from './island/islandReducer';
import {
  IslandEvent,
  IslandEventType,
  IslandSettings,
  IslandState,
  MusicTrack,
  createDefaultSettings,
} from './island/types';

type ActiveTab = 'simulator' | 'settings' | 'code';

const STORAGE_KEY = 'island_settings_v1';

const DEMO_TRACKS: MusicTrack[] = [
  { title: 'Midnight Refraction', artist: 'Kiasmos', isPlaying: true, positionSec: 68, durationSec: 214 },
  { title: 'Tides of Glass', artist: 'Rival Consoles', isPlaying: true, positionSec: 42, durationSec: 196 },
  { title: 'Solstice Drift', artist: 'Ólafur Arnalds', isPlaying: true, positionSec: 94, durationSec: 238 },
];

function loadSavedSettings(): IslandSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return createDefaultSettings();
    const parsed = JSON.parse(raw) as Partial<IslandSettings>;
    const defaults = createDefaultSettings();
    return clampSettings(
      {
        ...defaults,
        ...parsed,
        enabledEvents: {
          ...defaults.enabledEvents,
          ...(parsed.enabledEvents ?? {}),
        },
      },
      defaults
    );
  } catch {
    return createDefaultSettings();
  }
}

export default function App() {
  const [activeTab, setActiveTab] = useState<ActiveTab>('simulator');
  const [settings, setSettings] = useState<IslandSettings>(loadSavedSettings);
  const [islandState, setIslandState] = useState<IslandState>(createInitialIslandState);
  const [trackIndex, setTrackIndex] = useState(0);
  const [hapticCount, setHapticCount] = useState(0);
  const [downloading, setDownloading] = useState(false);
  const postExitShrinkTimer = useRef<number | null>(null);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch {
      // Ignore storage quota errors
    }
  }, [settings]);

  const triggerHapticTick = useCallback(() => {
    setHapticCount((c) => c + 1);
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      navigator.vibrate(10);
    }
  }, []);

  // Auto-collapse timer for non-sticky events (2s / 4s / 6s)
  useEffect(() => {
    const active = islandState.activeEvent;
    if (!active || isStickyEvent(active) || islandState.displayMode === 'IDLE') {
      return;
    }

    const extraMs =
      islandState.displayMode === 'EXPANDED'
        ? 1500
        : islandState.displayMode === 'FULL_EXPANDED'
        ? 4000
        : 0;
    const timeoutMs = settings.displayDurationSec * 1000 + extraMs;

    const timer = window.setTimeout(() => {
      setIslandState((prev) => advanceOrCollapseIsland(prev));
    }, timeoutMs);

    return () => window.clearTimeout(timer);
  }, [islandState.activeEvent, islandState.displayMode, settings.displayDurationSec]);

  // Live music progress ticker
  useEffect(() => {
    if (!islandState.persistentMusic?.isPlaying) return;
    const interval = window.setInterval(() => {
      setIslandState((prev) => {
        if (!prev.persistentMusic?.isPlaying) return prev;
        const nextPos =
          prev.persistentMusic.positionSec + 1 >= prev.persistentMusic.durationSec
            ? 0
            : prev.persistentMusic.positionSec + 1;
        const nextTrack = { ...prev.persistentMusic, positionSec: nextPos };
        return {
          ...prev,
          persistentMusic: nextTrack,
          activeEvent:
            prev.activeEvent?.type === 'MUSIC_PLAYER'
              ? { id: 'music_session', type: 'MUSIC_PLAYER', track: nextTrack }
              : prev.activeEvent,
        };
      });
    }, 1000);
    return () => window.clearInterval(interval);
  }, [islandState.persistentMusic?.isPlaying]);

  // Clear call status toast after 3s
  useEffect(() => {
    if (!islandState.callStatusMessage) return;
    const timer = window.setTimeout(() => {
      setIslandState((prev) => ({ ...prev, callStatusMessage: null }));
    }, 3000);
    return () => window.clearTimeout(timer);
  }, [islandState.callStatusMessage]);

  const handleTriggerEvent = useCallback(
    (event: IslandEvent) => {
      triggerHapticTick();
      setIslandState((prev) => enqueueIslandEvent(prev, event, settings));
    },
    [settings, triggerHapticTick]
  );

  const handleQueueBurst = useCallback(() => {
    triggerHapticTick();
    setIslandState((prev) => {
      let next = enqueueIslandEvent(
        prev,
        { id: `burst_1_${Date.now()}`, type: 'CHARGING_STARTED', batteryPercent: 84 },
        settings
      );
      next = enqueueIslandEvent(
        next,
        { id: `burst_2_${Date.now()}`, type: 'WIFI_CONNECTED', ssid: 'Studio-5G' },
        settings
      );
      next = enqueueIslandEvent(
        next,
        {
          id: `burst_3_${Date.now()}`,
          type: 'BLUETOOTH',
          deviceName: 'Sony WH-1000XM5',
          connected: true,
          isAudio: true,
          batteryLevel: 85,
        },
        settings
      );
      return next;
    });
  }, [settings, triggerHapticTick]);

  const handleUpdateSettings = useCallback((patch: Partial<IslandSettings>) => {
    setSettings((prev) => {
      const updated = clampSettings(patch, prev);
      if (!updated.masterEnabled) {
        setIslandState((s) => ({
          ...s,
          activeEvent: null,
          queue: [],
          displayMode: 'IDLE',
        }));
      }
      return updated;
    });
  }, []);

  const handleToggleEventType = useCallback((type: IslandEventType, enabled: boolean) => {
    setSettings((prev) => ({
      ...prev,
      enabledEvents: { ...prev.enabledEvents, [type]: enabled },
    }));
    if (!enabled) {
      setIslandState((prev) => {
        if (type === 'MUSIC_PLAYER') {
          const clearingMusic = prev.activeEvent?.type === 'MUSIC_PLAYER';
          return {
            ...prev,
            persistentMusic: null,
            activeEvent: clearingMusic ? null : prev.activeEvent,
            displayMode: clearingMusic ? 'IDLE' : prev.displayMode,
          };
        }
        return prev;
      });
    }
  }, []);

  const handleResetToDefault = useCallback(() => {
    setSettings((prev) => resetPositionAndSize(prev));
  }, []);

  const handleMediaAction = useCallback(
    (action: 'PLAY_PAUSE' | 'NEXT' | 'PREV') => {
      if (action === 'PLAY_PAUSE') {
        setIslandState((prev) => toggleMusicPlayPause(prev));
        return;
      }
      const nextIdx =
        action === 'NEXT'
          ? (trackIndex + 1) % DEMO_TRACKS.length
          : (trackIndex - 1 + DEMO_TRACKS.length) % DEMO_TRACKS.length;
      setTrackIndex(nextIdx);
      const nextTrack = { ...DEMO_TRACKS[nextIdx], isPlaying: true, positionSec: 0 };
      setIslandState((prev) =>
        enqueueIslandEvent(
          prev,
          { id: 'music_session', type: 'MUSIC_PLAYER', track: nextTrack },
          settings
        )
      );
    },
    [settings, trackIndex]
  );

  const handleMediaSeek = useCallback((positionSec: number) => {
    setIslandState((prev) => seekMusic(prev, positionSec));
  }, []);

  const handleSimulateAppExit = useCallback(() => {
    if (postExitShrinkTimer.current) {
      window.clearTimeout(postExitShrinkTimer.current);
      postExitShrinkTimer.current = null;
    }
    setIslandState((prev) => {
      const withMusic = prev.persistentMusic
        ? prev
        : enqueueIslandEvent(
            prev,
            { id: 'music_session', type: 'MUSIC_PLAYER', track: DEMO_TRACKS[trackIndex] },
            settings
          );
      return startAppTransition(
        withMusic,
        'EXIT_TO_ISLAND',
        settings,
        `exit_${Date.now()}`,
        '#3B1D7A'
      );
    });
  }, [settings, trackIndex]);

  const handleSimulateAppEnter = useCallback(() => {
    if (postExitShrinkTimer.current) {
      window.clearTimeout(postExitShrinkTimer.current);
      postExitShrinkTimer.current = null;
    }
    setIslandState((prev) => {
      const withMusic = prev.persistentMusic
        ? prev
        : enqueueIslandEvent(
            prev,
            { id: 'music_session', type: 'MUSIC_PLAYER', track: DEMO_TRACKS[trackIndex] },
            settings
          );
      return startAppTransition(
        withMusic,
        'ENTER_FROM_ISLAND',
        settings,
        `enter_${Date.now()}`,
        '#3B1D7A'
      );
    });
  }, [settings, trackIndex]);

  const handleExitReachedIsland = useCallback(() => {
    triggerHapticTick();
    setIslandState((prev) => onExitAnimationReachedIsland(prev));
    if (postExitShrinkTimer.current) {
      window.clearTimeout(postExitShrinkTimer.current);
    }
    postExitShrinkTimer.current = window.setTimeout(() => {
      setIslandState((prev) =>
        prev.activeEvent?.type === 'MUSIC_PLAYER' && prev.displayMode === 'EXPANDED'
          ? { ...prev, displayMode: 'COMPACT' }
          : prev
      );
      postExitShrinkTimer.current = null;
    }, 2000);
  }, [triggerHapticTick]);

  const handleTransitionFinished = useCallback((transitionId: string) => {
    setIslandState((prev) => finishAppTransition(prev, transitionId));
  }, []);

  const handleDownloadZip = async () => {
    setDownloading(true);
    try {
      const { downloadAndroidProjectZip } = await import('./island/androidProjectFiles');
      await downloadAndroidProjectZip();
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#F6F7F9] text-[#0F172A]">
      {/* 3-Zone Top Bar Contract */}
      <header className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-[#E4E7EC]">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-3 sm:gap-8">
          <a
            href="#top"
            onClick={(e) => {
              e.preventDefault();
              setActiveTab('simulator');
            }}
            className="font-display text-lg sm:text-xl font-bold tracking-tight text-[#0F172A] whitespace-nowrap shrink-0"
          >
            Island
          </a>

          <nav aria-label="Primary navigation" className="flex items-center gap-3.5 sm:gap-6">
            {(
              [
                { id: 'simulator', label: 'Simulator' },
                { id: 'settings', label: 'Settings' },
                { id: 'code', label: 'Android Code' },
              ] as const
            ).map((tab) => {
              const active = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id)}
                  className={`py-5 text-xs sm:text-sm font-medium border-b-2 transition-colors whitespace-nowrap shrink-0 ${
                    active
                      ? 'border-[#0F172A] text-[#0F172A]'
                      : 'border-transparent text-[#64748B] hover:text-[#0F172A]'
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}
          </nav>

          <button
            type="button"
            onClick={handleDownloadZip}
            disabled={downloading}
            aria-label="Download Android Project (.zip)"
            className="inline-flex items-center gap-1.5 px-3 sm:px-4 py-2 text-xs font-medium rounded-lg bg-[#0F172A] text-white hover:bg-[#1E293B] disabled:opacity-60 transition-colors whitespace-nowrap shrink-0"
          >
            <Download className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">{downloading ? 'Preparing...' : 'Download Project (.zip)'}</span>
            <span className="sm:hidden">{downloading ? '...' : 'ZIP'}</span>
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-6 py-8">
        {activeTab === 'code' ? (
          <Suspense
            fallback={
              <div className="rounded-2xl border border-[#E4E7EC] bg-white p-8 text-sm text-[#64748B]">
                Loading Android source tree...
              </div>
            }
          >
            <AndroidCodeExplorer />
          </Suspense>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Column: Live Android Overlay Simulator */}
            <section className="lg:col-span-5 lg:sticky lg:top-24 flex flex-col items-center">
              <PhoneSimulator
                state={islandState}
                settings={settings}
                hapticPulseCount={hapticCount}
                onTapIsland={() => {
                  triggerHapticTick();
                  setIslandState((prev) => tapIsland(prev));
                }}
                onLongPressIsland={() => {
                  triggerHapticTick();
                  setIslandState((prev) => longPressIsland(prev));
                }}
                onCollapseIsland={() => {
                  setIslandState((prev) => collapseIsland(prev));
                }}
                onToggleFullscreen={() => {
                  setIslandState((prev) => ({ ...prev, isFullscreen: !prev.isFullscreen }));
                }}
                onMediaAction={handleMediaAction}
                onMediaSeek={handleMediaSeek}
                onCallAction={(action) => {
                  setIslandState((prev) => resolveIncomingCall(prev, action));
                }}
                onSimulateAppExit={handleSimulateAppExit}
                onSimulateAppEnter={handleSimulateAppEnter}
                onExitTransitionReachedIsland={handleExitReachedIsland}
                onTransitionFinished={handleTransitionFinished}
              />
            </section>

            {/* Right Column: Event Triggers or Position/Size Settings */}
            <section className="lg:col-span-7 space-y-6">
              <div className="border-b border-[#E4E7EC] pb-4">
                <h1 className="font-display text-2xl sm:text-3xl font-bold tracking-tight text-[#0F172A] text-balance">
                  {activeTab === 'simulator'
                    ? 'Dynamic Island for Android'
                    : 'Island Position, Size & Behavior'}
                </h1>
                <p className="mt-1.5 text-sm text-[#475569] max-w-2xl">
                  {activeTab === 'simulator'
                    ? 'Spring-physics Jetpack Compose overlay service powered by WindowManager, MediaSessionManager, and system broadcast monitors.'
                    : 'Customize idle capsule geometry, screen offsets, duration, and event filters with DataStore persistence.'}
                </p>
              </div>

              {activeTab === 'simulator' ? (
                <EventTriggerPanel
                  settings={settings}
                  onTriggerEvent={handleTriggerEvent}
                  onTriggerQueueBurst={handleQueueBurst}
                />
              ) : (
                <SettingsPanel
                  settings={settings}
                  onUpdateSettings={handleUpdateSettings}
                  onToggleEventType={handleToggleEventType}
                  onResetToDefault={handleResetToDefault}
                />
              )}
            </section>
          </div>
        )}
      </main>
    </div>
  );
}

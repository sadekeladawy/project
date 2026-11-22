import React, { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Maximize2, Minimize2, ArrowUp, Expand, ChevronDown, LogOut, AppWindow } from 'lucide-react';
import { AppTransitionState, IslandSettings, IslandState } from '../island/types';
import { DynamicIslandCapsule } from './DynamicIslandCapsule';

interface PhoneSimulatorProps {
  state: IslandState;
  settings: IslandSettings;
  hapticPulseCount: number;
  onTapIsland: () => void;
  onLongPressIsland: () => void;
  onCollapseIsland: () => void;
  onToggleFullscreen: () => void;
  onMediaAction: (action: 'PLAY_PAUSE' | 'NEXT' | 'PREV') => void;
  onMediaSeek: (positionSec: number) => void;
  onCallAction: (action: 'ACCEPT' | 'DECLINE') => void;
  onSimulateAppExit: () => void;
  onSimulateAppEnter: () => void;
  onExitTransitionReachedIsland: () => void;
  onTransitionFinished: (transitionId: string) => void;
}

export const PhoneSimulator: React.FC<PhoneSimulatorProps> = ({
  state,
  settings,
  hapticPulseCount,
  onTapIsland,
  onLongPressIsland,
  onCollapseIsland,
  onToggleFullscreen,
  onMediaAction,
  onMediaSeek,
  onCallAction,
  onSimulateAppExit,
  onSimulateAppEnter,
  onExitTransitionReachedIsland,
  onTransitionFinished,
}) => {
  const [wallpaperFailed, setWallpaperFailed] = useState(false);
  const isHidden =
    !settings.masterEnabled || (settings.hideInFullscreen && state.isFullscreen);

  return (
    <div className="flex flex-col items-center gap-4">
      {/* Android Hardware Frame */}
      <div
        data-testid="phone-screen"
        onClick={() => {
          if (state.displayMode === 'EXPANDED' || state.displayMode === 'FULL_EXPANDED') {
            onCollapseIsland();
          }
        }}
        className="relative w-full max-w-[368px] h-[640px] sm:h-[680px] rounded-[46px] bg-[#090B10] border-[6px] border-[#1F2430] shadow-[0_24px_60px_rgba(15,23,42,0.18)] overflow-hidden select-none"
      >
        {/* Wallpaper or Fullscreen Player Layer */}
        {!wallpaperFailed && !state.isFullscreen && (
          <img
            src="/src/assets/images/phone_wallpaper_dark_1795029685636.jpg"
            alt="Android phone wallpaper"
            referrerPolicy="no-referrer"
            onError={() => setWallpaperFailed(true)}
            className="absolute inset-0 w-full h-full object-cover opacity-90"
          />
        )}

        {state.isFullscreen ? (
          <div className="absolute inset-0 bg-gradient-to-br from-[#0F172A] via-[#111827] to-[#020617] flex flex-col items-center justify-center p-6 text-center">
            <div className="w-14 h-14 rounded-2xl bg-white/10 flex items-center justify-center mb-3">
              <Maximize2 className="w-6 h-6 text-white/80" />
            </div>
            <p className="text-sm font-semibold text-white">Immersive Fullscreen App</p>
            <p className="text-xs text-zinc-400 mt-1 max-w-[220px]">
              {settings.hideInFullscreen
                ? 'Hide in fullscreen is enabled — Island fades away during video or games.'
                : 'Hide in fullscreen is disabled — Island stays visible over fullscreen content.'}
            </p>
          </div>
        ) : (
          <div className="absolute inset-0 bg-gradient-to-b from-black/35 via-transparent to-black/70 pointer-events-none" />
        )}

        {/* Full-screen iPhone-style App Exit / Reverse Enter Spring Morph Overlay */}
        <AnimatePresence>
          {state.activeTransition && (
            <AppTransitionOverlayLayer
              key={state.activeTransition.id}
              transition={state.activeTransition}
              settings={settings}
              onReachedIsland={onExitTransitionReachedIsland}
              onFinished={onTransitionFinished}
            />
          )}
        </AnimatePresence>

        {/* Android Status Bar (hidden when fullscreen, dims cleanly when Island widens) */}
        {!state.isFullscreen && (
          <div
            className={`relative z-10 flex items-center justify-between px-6 pt-3.5 text-[12px] font-medium text-white/90 tabular-nums pointer-events-none transition-opacity duration-200 ${
              state.activeEvent && state.displayMode !== 'IDLE' ? 'opacity-25' : 'opacity-100'
            }`}
          >
            <span>11:18</span>
            <div className="flex items-center gap-1.5 text-[11px] text-white/85">
              <span>5G</span>
              <span>84%</span>
            </div>
          </div>
        )}

        {/* Floating Dynamic Island Window */}
        <div className="absolute top-0 left-0 right-0 z-30 flex justify-center pointer-events-none">
          <DynamicIslandCapsule
            activeEvent={state.activeEvent}
            displayMode={state.displayMode}
            queueCount={state.queue.length}
            settings={settings}
            isHidden={isHidden}
            onTap={onTapIsland}
            onLongPress={onLongPressIsland}
            onSwipeUp={onCollapseIsland}
            onMediaAction={onMediaAction}
            onMediaSeek={onMediaSeek}
            onCallAction={onCallAction}
          />
        </div>

        {/* Simulated Android App Grid & Call Toast */}
        {!state.isFullscreen && (
          <div className="relative z-10 h-full flex flex-col justify-end px-6 pb-10 pointer-events-none">
            {state.callStatusMessage && (
              <div className="mb-4 px-3.5 py-2.5 rounded-2xl bg-black/75 backdrop-blur-md border border-white/10 text-xs text-white text-center">
                {state.callStatusMessage}
              </div>
            )}

            <div className="rounded-3xl bg-white/10 backdrop-blur-md border border-white/10 p-4 text-white">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-xs text-zinc-300">Overlay Service</p>
                  <p className="text-sm font-semibold mt-0.5">
                    {settings.masterEnabled ? 'Running over all apps' : 'Paused'}
                  </p>
                </div>
                <span className="text-xs font-mono text-zinc-300 tabular-nums">
                  {state.displayMode}
                </span>
              </div>
              <div className="mt-3 pt-3 border-t border-white/10 flex items-center justify-between text-[11px] text-zinc-300 tabular-nums">
                <span>Capsule: {settings.idleWidth}×{settings.idleHeight} dp</span>
                <span>Exit morph: {settings.appExitAnimationEnabled ? `${settings.animationSpeed.toFixed(2)}x` : 'Off'}</span>
              </div>
            </div>

            {/* Bottom Gesture Bar */}
            <div className="mt-6 flex justify-center">
              <div className="w-28 h-1 rounded-full bg-white/40" />
            </div>
          </div>
        )}
      </div>

      {/* App Exit / Re-open Spring Morph Simulation Controls */}
      <div className="w-full max-w-[368px] grid grid-cols-2 gap-2">
        <button
          type="button"
          data-testid="btn-simulate-app-exit"
          onClick={onSimulateAppExit}
          className="inline-flex items-center justify-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg bg-[#0F172A] text-white hover:bg-[#1E293B] transition-colors whitespace-nowrap"
        >
          <LogOut className="w-3.5 h-3.5" />
          Simulate App Exit
        </button>
        <button
          type="button"
          data-testid="btn-simulate-app-enter"
          onClick={onSimulateAppEnter}
          className="inline-flex items-center justify-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg border border-[#E4E7EC] bg-white text-[#0F172A] hover:bg-[#F8FAFC] transition-colors whitespace-nowrap"
        >
          <AppWindow className="w-3.5 h-3.5" />
          Re-open Media App
        </button>
      </div>

      {/* Interactive Gesture & Mode Controls */}
      <div className="w-full max-w-[368px] flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={onTapIsland}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg border border-[#E4E7EC] bg-white text-[#0F172A] hover:bg-[#F8FAFC] transition-colors whitespace-nowrap"
          >
            <ChevronDown className="w-3.5 h-3.5" />
            Tap Expand
          </button>
          <button
            type="button"
            onClick={onLongPressIsland}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg border border-[#E4E7EC] bg-white text-[#0F172A] hover:bg-[#F8FAFC] transition-colors whitespace-nowrap"
          >
            <Expand className="w-3.5 h-3.5" />
            Long-Press
          </button>
          <button
            type="button"
            onClick={onCollapseIsland}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg border border-[#E4E7EC] bg-white text-[#0F172A] hover:bg-[#F8FAFC] transition-colors whitespace-nowrap"
          >
            <ArrowUp className="w-3.5 h-3.5" />
            Collapse
          </button>
        </div>

        <button
          type="button"
          onClick={onToggleFullscreen}
          aria-pressed={state.isFullscreen}
          className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg border transition-colors whitespace-nowrap ${
            state.isFullscreen
              ? 'bg-[#0F172A] text-white border-[#0F172A]'
              : 'bg-white text-[#0F172A] border-[#E4E7EC] hover:bg-[#F8FAFC]'
          }`}
        >
          {state.isFullscreen ? <Minimize2 className="w-3.5 h-3.5" /> : <Maximize2 className="w-3.5 h-3.5" />}
          {state.isFullscreen ? 'Exit Fullscreen' : 'Fullscreen App'}
        </button>
      </div>

      <p className="text-xs text-[#64748B] tabular-nums">
        Haptic ticks triggered: {hapticPulseCount} · Tap capsule to expand, hold for full view, swipe up to dismiss
      </p>
    </div>
  );
};

function AppTransitionOverlayLayer({
  transition,
  settings,
  onReachedIsland,
  onFinished,
}: {
  transition: AppTransitionState;
  settings: IslandSettings;
  onReachedIsland: () => void;
  onFinished: (transitionId: string) => void;
}) {
  const isExit = transition.direction === 'EXIT_TO_ISLAND';
  const speed = Math.max(0.5, Math.min(2.0, settings.animationSpeed));
  const durationSec = 0.56 / speed;
  const reachedRef = React.useRef(onReachedIsland);
  const finishedRef = React.useRef(onFinished);
  reachedRef.current = onReachedIsland;
  finishedRef.current = onFinished;

  useEffect(() => {
    const reachMs = Math.round(durationSec * 620);
    const doneMs = Math.round(durationSec * 1050);
    const reachTimer = isExit
      ? window.setTimeout(() => {
          reachedRef.current();
        }, reachMs)
      : null;
    const finishTimer = window.setTimeout(() => {
      finishedRef.current(transition.id);
    }, doneMs);

    return () => {
      if (reachTimer) window.clearTimeout(reachTimer);
      window.clearTimeout(finishTimer);
    };
  }, [durationSec, isExit, transition.id]);

  return (
    <motion.div
      data-testid="app-transition-overlay"
      data-direction={transition.direction}
      initial={
        isExit
          ? {
              scale: 1,
              x: 0,
              y: 0,
              borderRadius: 40,
              opacity: 0.98,
            }
          : {
              scale: 0.11,
              x: settings.offsetX,
              y: settings.offsetY + 4,
              borderRadius: 180,
              opacity: 0.95,
            }
      }
      animate={
        isExit
          ? {
              scale: 0.11,
              x: settings.offsetX,
              y: settings.offsetY + 4,
              borderRadius: 180,
              opacity: [0.98, 0.96, 0],
            }
          : {
              scale: 1,
              x: 0,
              y: 0,
              borderRadius: 40,
              opacity: [0.95, 0.92, 0],
            }
      }
      transition={{
        scale: { type: 'spring', stiffness: 260 * speed * speed, damping: 24 },
        x: { type: 'spring', stiffness: 260 * speed * speed, damping: 26 },
        y: { type: 'spring', stiffness: 260 * speed * speed, damping: 26 },
        borderRadius: { duration: durationSec * 0.85, ease: [0.16, 1, 0.3, 1] },
        opacity: { duration: durationSec, times: [0, 0.62, 1], ease: 'easeOut' },
      }}
      style={{
        transformOrigin: '50% 0%',
        background: `linear-gradient(180deg, ${transition.dominantColor} 0%, #11131F 100%)`,
      }}
      className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-4 pointer-events-none"
    >
      <img
        src="/src/assets/images/album_cover_midnight_1795029689726.jpg"
        alt={transition.title}
        referrerPolicy="no-referrer"
        className="w-40 h-40 rounded-3xl object-cover shadow-2xl border border-white/15"
      />
      <div className="text-center px-6">
        <p className="text-base font-semibold text-white">{transition.title}</p>
        <p className="text-xs text-white/75 mt-0.5">{transition.artist}</p>
      </div>
    </motion.div>
  );
}

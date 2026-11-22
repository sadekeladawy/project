import React, { useRef } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Battery,
  BatteryCharging,
  BatteryWarning,
  Bell,
  BellOff,
  Bluetooth,
  BluetoothOff,
  CheckCircle2,
  ChevronUp,
  Headphones,
  Pause,
  Phone,
  PhoneOff,
  Plane,
  Play,
  SkipBack,
  SkipForward,
  Vibrate,
  Wifi,
  WifiOff,
  WifiZero,
  Zap,
} from 'lucide-react';
import { getIslandDimensions, getIslandVerticalOffset } from '../island/islandReducer';
import { IslandDisplayMode, IslandEvent, IslandSettings } from '../island/types';

interface DynamicIslandCapsuleProps {
  activeEvent: IslandEvent | null;
  displayMode: IslandDisplayMode;
  queueCount: number;
  settings: IslandSettings;
  isHidden: boolean;
  onTap: () => void;
  onLongPress: () => void;
  onSwipeUp: () => void;
  onMediaAction: (action: 'PLAY_PAUSE' | 'NEXT' | 'PREV') => void;
  onMediaSeek?: (positionSec: number) => void;
  onCallAction: (action: 'ACCEPT' | 'DECLINE') => void;
  scaleFactor?: number;
}

const SPRING_TRANSITION = {
  type: 'spring' as const,
  stiffness: 265,
  damping: 22,
  mass: 0.85,
};

const ICON_POP = {
  initial: { scale: 0.55, opacity: 0 },
  animate: { scale: 1, opacity: 1 },
  transition: { type: 'spring' as const, stiffness: 360, damping: 18 },
};

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function EqualizerBars({ playing }: { playing: boolean }) {
  return (
    <div className="flex items-center gap-[2.5px] h-3.5" aria-hidden="true">
      <span
        className={`w-[2.5px] h-3.5 rounded-full bg-[#BF5AF2] origin-center ${
          playing ? 'animate-eq-1' : 'scale-y-[0.28]'
        }`}
      />
      <span
        className={`w-[2.5px] h-3.5 rounded-full bg-[#BF5AF2] origin-center ${
          playing ? 'animate-eq-2' : 'scale-y-[0.28]'
        }`}
      />
      <span
        className={`w-[2.5px] h-3.5 rounded-full bg-[#BF5AF2] origin-center ${
          playing ? 'animate-eq-3' : 'scale-y-[0.28]'
        }`}
      />
      <span
        className={`w-[2.5px] h-3.5 rounded-full bg-[#BF5AF2] origin-center ${
          playing ? 'animate-eq-4' : 'scale-y-[0.28]'
        }`}
      />
    </div>
  );
}

function BatteryRingSvg({ percent, color, size = 22 }: { percent: number; color: string; size?: number }) {
  const radius = (size - 4) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (Math.min(100, Math.max(0, percent)) / 100) * circumference;

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90 shrink-0">
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={color}
        strokeOpacity={0.22}
        strokeWidth={2.5}
      />
      <motion.circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth={2.5}
        strokeLinecap="round"
        strokeDasharray={circumference}
        initial={{ strokeDashoffset: circumference }}
        animate={{ strokeDashoffset: offset }}
        transition={{ duration: 0.75, ease: [0.16, 1, 0.3, 1] }}
      />
    </svg>
  );
}

export const DynamicIslandCapsule: React.FC<DynamicIslandCapsuleProps> = ({
  activeEvent,
  displayMode,
  queueCount,
  settings,
  isHidden,
  onTap,
  onLongPress,
  onSwipeUp,
  onMediaAction,
  onMediaSeek,
  onCallAction,
  scaleFactor = 1,
}) => {
  const dims = getIslandDimensions(displayMode, activeEvent, settings);
  const verticalOffset = getIslandVerticalOffset(displayMode, activeEvent, settings);
  const isExpanded =
    activeEvent !== null && (displayMode === 'EXPANDED' || displayMode === 'FULL_EXPANDED');
  const isInteractiveExpanded =
    isExpanded &&
    (activeEvent?.type === 'MUSIC_PLAYER' || activeEvent?.type === 'INCOMING_CALL');

  const longPressTimer = useRef<number | null>(null);
  const longPressTriggered = useRef(false);
  const dragStartX = useRef<number | null>(null);
  const dragStartY = useRef<number | null>(null);

  const handlePointerDown = (e: React.PointerEvent) => {
    dragStartX.current = e.clientX;
    dragStartY.current = e.clientY;
    longPressTriggered.current = false;
    if (!isInteractiveExpanded) {
      longPressTimer.current = window.setTimeout(() => {
        longPressTriggered.current = true;
        onLongPress();
      }, 420);
    }
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (dragStartY.current !== null && dragStartX.current !== null) {
      const dy = e.clientY - dragStartY.current;
      const dx = e.clientX - dragStartX.current;
      if (dy < -34 && Math.abs(dy) > Math.abs(dx) * 1.5) {
        if (longPressTimer.current) {
          clearTimeout(longPressTimer.current);
          longPressTimer.current = null;
        }
        if (displayMode !== 'IDLE') {
          dragStartX.current = null;
          dragStartY.current = null;
          onSwipeUp();
        }
      }
    }
  };

  const handlePointerUp = () => {
    if (longPressTimer.current) {
      clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
    if (!longPressTriggered.current && !isInteractiveExpanded) {
      onTap();
    }
    dragStartX.current = null;
    dragStartY.current = null;
  };

  const handlePointerCancel = () => {
    if (longPressTimer.current) {
      clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
    dragStartX.current = null;
    dragStartY.current = null;
  };

  const contentKey = activeEvent
    ? `${activeEvent.type}_${activeEvent.id}_${displayMode}`
    : 'IDLE';

  return (
    <div className="relative z-50 flex items-center justify-center gap-2">
      <motion.div
        data-testid="dynamic-island-capsule"
        data-mode={activeEvent ? displayMode : 'IDLE'}
        data-event={activeEvent?.type ?? 'NONE'}
        data-hidden={isHidden ? 'true' : 'false'}
        role="button"
        tabIndex={0}
        aria-label={
          activeEvent
            ? `Dynamic Island showing ${activeEvent.type.toLowerCase().replace('_', ' ')} in ${displayMode.toLowerCase()} mode`
            : 'Dynamic Island idle capsule'
        }
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onTap();
          }
        }}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerCancel}
        onClick={(e) => e.stopPropagation()}
        initial={false}
        animate={{
          width: dims.width * scaleFactor,
          height: dims.height * scaleFactor,
          borderRadius: dims.radius * scaleFactor,
          x: settings.offsetX * scaleFactor,
          y: verticalOffset * scaleFactor,
          opacity: isHidden ? 0 : 1,
          scale: isHidden ? 0.8 : 1,
        }}
        transition={SPRING_TRANSITION}
        className={`relative z-50 bg-black text-white border border-white/15 shadow-[0_14px_38px_rgba(0,0,0,0.65)] overflow-hidden select-none flex items-center justify-center focus-visible:outline-2 focus-visible:outline-[#3B82F6] ${
          isInteractiveExpanded ? 'cursor-default' : 'cursor-pointer'
        }`}
        style={{ pointerEvents: isHidden ? 'none' : 'auto' }}
      >
        <AnimatePresence mode="popLayout" initial={false}>
          <motion.div
            key={contentKey}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
            className="w-full h-full flex items-center justify-center"
          >
            {!activeEvent || displayMode === 'IDLE' ? (
              <div className="w-full h-full flex items-center justify-end pr-3">
                <div className="w-2.5 h-2.5 rounded-full bg-[#11131A] border border-white/10" />
              </div>
            ) : (
              <IslandCardBody
                event={activeEvent}
                mode={displayMode}
                onCollapse={onSwipeUp}
                onMediaAction={onMediaAction}
                onMediaSeek={onMediaSeek}
                onCallAction={onCallAction}
              />
            )}
          </motion.div>
        </AnimatePresence>
      </motion.div>

      <AnimatePresence>
        {queueCount > 0 && activeEvent && displayMode === 'COMPACT' && !isHidden && (
          <motion.div
            initial={{ opacity: 0, scale: 0.6, y: verticalOffset * scaleFactor }}
            animate={{
              opacity: 1,
              scale: 1,
              x: settings.offsetX * scaleFactor,
              y: verticalOffset * scaleFactor,
            }}
            exit={{ opacity: 0, scale: 0.6 }}
            transition={SPRING_TRANSITION}
            className="h-8 min-w-8 px-1.5 rounded-full bg-black text-zinc-200 border border-white/15 shadow-[0_10px_28px_rgba(0,0,0,0.6)] flex items-center justify-center text-[11px] font-mono font-semibold tabular-nums"
          >
            +{queueCount}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

function IslandCardBody({
  event,
  mode,
  onCollapse,
  onMediaAction,
  onMediaSeek,
  onCallAction,
}: {
  event: IslandEvent;
  mode: IslandDisplayMode;
  onCollapse: () => void;
  onMediaAction: (action: 'PLAY_PAUSE' | 'NEXT' | 'PREV') => void;
  onMediaSeek?: (positionSec: number) => void;
  onCallAction: (action: 'ACCEPT' | 'DECLINE') => void;
}) {
  const isCompact = mode === 'COMPACT';
  const isFull = mode === 'FULL_EXPANDED';

  switch (event.type) {
    case 'CHARGING_STARTED':
      return isCompact ? (
        <div className="w-full px-3.5 flex items-center justify-between text-xs">
          <div className="flex items-center gap-1.5">
            <motion.span {...ICON_POP}>
              <Zap className="w-4 h-4 text-[#30D158] fill-[#30D158]" />
            </motion.span>
            <span className="font-medium text-white">Charging</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="font-semibold text-[#30D158] tabular-nums">{event.batteryPercent}%</span>
            <BatteryRingSvg percent={event.batteryPercent} color="#30D158" size={19} />
          </div>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="relative w-10 h-10 flex items-center justify-center">
              <BatteryRingSvg percent={event.batteryPercent} color="#30D158" size={40} />
              <Zap className="w-4 h-4 text-[#30D158] fill-[#30D158] absolute" />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">Charging Connected</p>
              <p className="text-xs text-zinc-400">
                {isFull ? 'Fast power delivery · Estimated 24m to full' : 'Power adapter attached'}
              </p>
            </div>
          </div>
          <span className="text-lg font-bold text-[#30D158] tabular-nums">{event.batteryPercent}%</span>
        </div>
      );

    case 'CHARGING_STOPPED':
      return isCompact ? (
        <div className="w-full px-3.5 flex items-center justify-between text-xs">
          <div className="flex items-center gap-1.5">
            <motion.span {...ICON_POP}>
              <Battery className="w-4 h-4 text-zinc-300" />
            </motion.span>
            <span className="font-medium text-white">Unplugged</span>
          </div>
          <span className="font-semibold text-zinc-400 tabular-nums">{event.batteryPercent}%</span>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Battery className="w-7 h-7 text-zinc-200" />
            <div>
              <p className="text-sm font-semibold text-white">Charger Disconnected</p>
              <p className="text-xs text-zinc-400">Running on battery power</p>
            </div>
          </div>
          <span className="text-lg font-semibold text-white tabular-nums">{event.batteryPercent}%</span>
        </div>
      );

    case 'BATTERY_FULL':
      return isCompact ? (
        <div className="w-full px-3.5 flex items-center justify-between text-xs">
          <div className="flex items-center gap-1.5">
            <motion.span {...ICON_POP}>
              <CheckCircle2 className="w-4 h-4 text-[#30D158]" />
            </motion.span>
            <span className="font-medium text-white">Battery Full</span>
          </div>
          <span className="font-bold text-[#30D158] tabular-nums">100%</span>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <BatteryCharging className="w-7 h-7 text-[#30D158]" />
            <div>
              <p className="text-sm font-semibold text-white">Battery Full</p>
              <p className="text-xs text-zinc-400">100% charged · Safe to unplug</p>
            </div>
          </div>
          <CheckCircle2 className="w-6 h-6 text-[#30D158]" />
        </div>
      );

    case 'LOW_BATTERY': {
      const color = event.isCritical ? '#FF453A' : '#FF9F0A';
      return isCompact ? (
        <div className="w-full px-3.5 flex items-center justify-between text-xs">
          <div className="flex items-center gap-1.5">
            <motion.span {...ICON_POP}>
              <BatteryWarning className="w-4 h-4" style={{ color }} />
            </motion.span>
            <span className="font-semibold" style={{ color }}>
              {event.isCritical ? 'Critical Battery' : 'Low Battery'}
            </span>
          </div>
          <span className="font-bold tabular-nums" style={{ color }}>
            {event.batteryPercent}%
          </span>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="relative w-9 h-9 flex items-center justify-center">
              <BatteryRingSvg percent={event.batteryPercent} color={color} size={36} />
              <BatteryWarning className="w-4 h-4 absolute" style={{ color }} />
            </div>
            <div>
              <p className="text-sm font-semibold text-white">
                {event.isCritical ? 'Critical Battery' : 'Low Battery'} ({event.batteryPercent}%)
              </p>
              <p className="text-xs text-zinc-400">
                {event.isCritical ? 'Connect charger immediately' : 'Consider enabling Battery Saver'}
              </p>
            </div>
          </div>
          <span className="text-base font-bold tabular-nums" style={{ color }}>
            {event.batteryPercent}%
          </span>
        </div>
      );
    }

    case 'WIFI_CONNECTED':
      return isCompact ? (
        <div className="w-full px-3.5 flex items-center justify-between text-xs gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <motion.span {...ICON_POP}>
              <Wifi className="w-4 h-4 text-[#0A84FF] shrink-0" />
            </motion.span>
            <span className="font-medium text-white truncate">{event.ssid}</span>
          </div>
          <span className="text-[11px] font-semibold text-[#0A84FF] shrink-0">Wi-Fi</span>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <Wifi className="w-6 h-6 text-[#0A84FF] shrink-0" />
            <div className="min-w-0">
              <p className="text-sm font-semibold text-white truncate">{event.ssid}</p>
              <p className="text-xs text-zinc-400">Wi-Fi connected · 5 GHz</p>
            </div>
          </div>
          <span className="text-xs font-semibold text-[#0A84FF]">Online</span>
        </div>
      );

    case 'WIFI_DISCONNECTED':
      return isCompact ? (
        <div className="w-full px-3.5 flex items-center gap-2 text-xs">
          <motion.span {...ICON_POP}>
            <WifiOff className="w-4 h-4 text-[#FF453A]" />
          </motion.span>
          <span className="font-medium text-white">Wi-Fi Disconnected</span>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center gap-3">
          <WifiOff className="w-6 h-6 text-[#FF453A]" />
          <div>
            <p className="text-sm font-semibold text-white">Wi-Fi Disconnected</p>
            <p className="text-xs text-zinc-400">No wireless network connection</p>
          </div>
        </div>
      );

    case 'HOTSPOT':
      return isCompact ? (
        <div className="w-full px-3.5 flex items-center justify-between text-xs">
          <div className="flex items-center gap-1.5">
            <motion.span {...ICON_POP}>
              {event.enabled ? (
                <Wifi className="w-4 h-4 text-[#30D158]" />
              ) : (
                <WifiZero className="w-4 h-4 text-zinc-400" />
              )}
            </motion.span>
            <span className="font-medium text-white">
              {event.enabled ? 'Hotspot On' : 'Hotspot Off'}
            </span>
          </div>
          <span className={`text-[11px] font-semibold ${event.enabled ? 'text-[#30D158]' : 'text-zinc-400'}`}>
            {event.enabled ? 'Active' : 'Off'}
          </span>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center gap-3">
          {event.enabled ? (
            <Wifi className="w-6 h-6 text-[#30D158]" />
          ) : (
            <WifiZero className="w-6 h-6 text-zinc-400" />
          )}
          <div>
            <p className="text-sm font-semibold text-white">
              {event.enabled ? 'Personal Hotspot On' : 'Personal Hotspot Off'}
            </p>
            <p className="text-xs text-zinc-400">
              {event.enabled ? 'Sharing mobile data connection' : 'Tethering disabled'}
            </p>
          </div>
        </div>
      );

    case 'BLUETOOTH': {
      const Icon = !event.connected ? BluetoothOff : event.isAudio ? Headphones : Bluetooth;
      const tint = event.connected ? '#64D2FF' : '#A1A1AA';
      return isCompact ? (
        <div className="w-full px-3.5 flex items-center justify-between text-xs gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <motion.span {...ICON_POP}>
              <Icon className="w-4 h-4 shrink-0" style={{ color: tint }} />
            </motion.span>
            <span className="font-medium text-white truncate">{event.deviceName}</span>
          </div>
          <span className="text-[11px] font-semibold shrink-0" style={{ color: tint }}>
            {event.connected ? 'Connected' : 'Off'}
          </span>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <Icon className="w-6 h-6 shrink-0" style={{ color: tint }} />
            <div className="min-w-0">
              <p className="text-sm font-semibold text-white truncate">{event.deviceName}</p>
              <p className="text-xs text-zinc-400">
                {event.connected
                  ? event.isAudio
                    ? 'Audio device connected'
                    : 'Bluetooth paired'
                  : 'Device disconnected'}
              </p>
            </div>
          </div>
          {event.batteryLevel !== undefined && event.connected && (
            <span className="text-sm font-semibold text-[#64D2FF] tabular-nums">
              {event.batteryLevel}%
            </span>
          )}
        </div>
      );
    }

    case 'AIRPLANE_MODE':
      return isCompact ? (
        <div className="w-full px-3.5 flex items-center justify-between text-xs">
          <div className="flex items-center gap-2">
            <motion.span {...ICON_POP}>
              <Plane className={`w-4 h-4 ${event.enabled ? 'text-[#FF9F0A]' : 'text-[#30D158]'}`} />
            </motion.span>
            <span className="font-medium text-white">
              {event.enabled ? 'Airplane On' : 'Airplane Off'}
            </span>
          </div>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center gap-3">
          <Plane className={`w-6 h-6 ${event.enabled ? 'text-[#FF9F0A]' : 'text-[#30D158]'}`} />
          <div>
            <p className="text-sm font-semibold text-white">
              {event.enabled ? 'Airplane Mode On' : 'Airplane Mode Off'}
            </p>
            <p className="text-xs text-zinc-400">
              {event.enabled ? 'Wireless radios suspended' : 'Cellular & Wi-Fi restored'}
            </p>
          </div>
        </div>
      );

    case 'RINGER_MODE': {
      const cfg =
        event.mode === 'SILENT'
          ? { label: 'Silent', Icon: BellOff, color: '#FF453A', sub: 'Calls and alerts muted' }
          : event.mode === 'VIBRATE'
          ? { label: 'Vibrate', Icon: Vibrate, color: '#FF9F0A', sub: 'Haptic alerts active' }
          : { label: 'Ring', Icon: Bell, color: '#30D158', sub: 'Ringtone volume active' };

      return isCompact ? (
        <div className="w-full px-3.5 flex items-center justify-between text-xs">
          <div className="flex items-center gap-2">
            <motion.span {...ICON_POP}>
              <cfg.Icon className="w-4 h-4" style={{ color: cfg.color }} />
            </motion.span>
            <span className="font-medium text-white">{cfg.label}</span>
          </div>
          <span className="text-[11px] text-zinc-400">Mode</span>
        </div>
      ) : (
        <div className="w-full px-4 flex items-center gap-3">
          <cfg.Icon className="w-6 h-6" style={{ color: cfg.color }} />
          <div>
            <p className="text-sm font-semibold text-white">{cfg.label} Mode</p>
            <p className="text-xs text-zinc-400">{cfg.sub}</p>
          </div>
        </div>
      );
    }

    case 'INCOMING_CALL':
      return isCompact ? (
        <div className="w-full px-3 flex items-center justify-between text-[11px] gap-1.5">
          <div className="flex items-center gap-1.5 min-w-0">
            <motion.span {...ICON_POP}>
              <Phone className="w-3.5 h-3.5 text-[#30D158] shrink-0" />
            </motion.span>
            <span className="font-semibold text-white truncate">{event.callerName}</span>
          </div>
          <span className="text-[10px] font-medium text-[#30D158] shrink-0">Call</span>
        </div>
      ) : (
        <div className="w-full px-4 py-2.5 flex items-center justify-between gap-3">
          <div className="min-w-0">
            <p className="text-[11px] font-semibold text-[#30D158]">Incoming Call</p>
            <p className="text-sm font-bold text-white truncate">{event.callerName}</p>
            {isFull && <p className="text-xs text-zinc-400 tabular-nums">{event.phoneNumber}</p>}
          </div>
          <div
            className="flex items-center gap-2 shrink-0"
            onPointerDown={(e) => e.stopPropagation()}
            onPointerUp={(e) => e.stopPropagation()}
          >
            <button
              type="button"
              aria-label="Decline call"
              onClick={(e) => {
                e.stopPropagation();
                onCallAction('DECLINE');
              }}
              className="w-9 h-9 rounded-full bg-[#FF453A] hover:bg-[#ff5a50] active:scale-95 flex items-center justify-center transition-transform cursor-pointer"
            >
              <PhoneOff className="w-4 h-4 text-white" />
            </button>
            <button
              type="button"
              aria-label="Accept call"
              onClick={(e) => {
                e.stopPropagation();
                onCallAction('ACCEPT');
              }}
              className="w-9 h-9 rounded-full bg-[#30D158] hover:bg-[#41df67] active:scale-95 flex items-center justify-center transition-transform cursor-pointer"
            >
              <Phone className="w-4 h-4 text-white" />
            </button>
          </div>
        </div>
      );

    case 'MUSIC_PLAYER': {
      return isCompact ? (
        <div className="w-full px-2.5 flex items-center justify-between gap-2">
          <img
            src="/src/assets/images/album_cover_midnight_1795029689726.jpg"
            alt={event.track.title}
            referrerPolicy="no-referrer"
            className="w-5 h-5 rounded-md object-cover shrink-0 border border-white/10"
          />
          <span className="text-[11px] font-medium text-white truncate flex-1">{event.track.title}</span>
          <EqualizerBars playing={event.track.isPlaying} />
        </div>
      ) : (
        <div className="w-full h-full px-3.5 py-2.5 flex flex-col justify-between">
          <div className="flex items-center gap-2.5">
            <img
              src="/src/assets/images/album_cover_midnight_1795029689726.jpg"
              alt={event.track.title}
              referrerPolicy="no-referrer"
              className={`${isFull ? 'w-11 h-11' : 'w-9 h-9'} rounded-lg object-cover shrink-0 border border-white/10`}
            />
            <div className="min-w-0 flex-1">
              <p className="text-[13px] font-semibold text-white truncate leading-tight">{event.track.title}</p>
              <p className="text-[11px] text-zinc-400 truncate mt-0.5">{event.track.artist}</p>
            </div>
            <EqualizerBars playing={event.track.isPlaying} />
            <button
              type="button"
              aria-label="Collapse music player"
              onPointerDown={(e) => e.stopPropagation()}
              onPointerUp={(e) => e.stopPropagation()}
              onClick={(e) => {
                e.stopPropagation();
                onCollapse();
              }}
              className="w-6 h-6 rounded-full bg-white/10 hover:bg-white/20 active:scale-95 flex items-center justify-center transition-transform cursor-pointer shrink-0"
            >
              <ChevronUp className="w-3.5 h-3.5 text-zinc-300" />
            </button>
          </div>

          <div
            className="space-y-0.5"
            onPointerDown={(e) => e.stopPropagation()}
            onPointerMove={(e) => e.stopPropagation()}
            onPointerUp={(e) => e.stopPropagation()}
            onClick={(e) => e.stopPropagation()}
          >
            <input
              type="range"
              aria-label="Seek track position"
              min={0}
              max={Math.max(1, event.track.durationSec)}
              value={Math.min(event.track.durationSec, Math.max(0, event.track.positionSec))}
              onChange={(e) => onMediaSeek?.(Number(e.target.value))}
              className="w-full h-1.5 rounded-full bg-white/20 accent-white cursor-pointer"
            />
            <div className="flex justify-between text-[10px] font-mono text-zinc-400 tabular-nums">
              <span>{formatTime(event.track.positionSec)}</span>
              <span>{formatTime(event.track.durationSec)}</span>
            </div>
          </div>

          <div
            className="flex items-center justify-center gap-5"
            onPointerDown={(e) => e.stopPropagation()}
            onPointerUp={(e) => e.stopPropagation()}
          >
            <button
              type="button"
              aria-label="Previous track"
              onClick={(e) => {
                e.stopPropagation();
                onMediaAction('PREV');
              }}
              className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 active:scale-95 flex items-center justify-center transition-transform cursor-pointer"
            >
              <SkipBack className="w-3.5 h-3.5 text-white fill-white" />
            </button>
            <button
              type="button"
              aria-label={event.track.isPlaying ? 'Pause music' : 'Play music'}
              onClick={(e) => {
                e.stopPropagation();
                onMediaAction('PLAY_PAUSE');
              }}
              className="w-9 h-9 rounded-full bg-white/20 hover:bg-white/30 active:scale-95 flex items-center justify-center transition-transform cursor-pointer"
            >
              {event.track.isPlaying ? (
                <Pause className="w-4 h-4 text-white fill-white" />
              ) : (
                <Play className="w-4 h-4 text-white fill-white ml-0.5" />
              )}
            </button>
            <button
              type="button"
              aria-label="Next track"
              onClick={(e) => {
                e.stopPropagation();
                onMediaAction('NEXT');
              }}
              className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 active:scale-95 flex items-center justify-center transition-transform cursor-pointer"
            >
              <SkipForward className="w-3.5 h-3.5 text-white fill-white" />
            </button>
          </div>
        </div>
      );
    }

    case 'MUSIC_SESSION_ENDED':
      return null;
  }
}

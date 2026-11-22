import React from 'react';
import {
  Battery,
  BatteryCharging,
  BatteryWarning,
  Bell,
  BellOff,
  Bluetooth,
  BluetoothOff,
  CheckCircle2,
  Headphones,
  Layers,
  Music,
  PhoneIncoming,
  Plane,
  Radio,
  Vibrate,
  Wifi,
  WifiOff,
  Zap,
} from 'lucide-react';
import { IslandEvent, IslandSettings } from '../island/types';

interface EventTriggerPanelProps {
  settings: IslandSettings;
  onTriggerEvent: (event: IslandEvent) => void;
  onTriggerQueueBurst: () => void;
}

let seq = 100;
function nextId(prefix: string): string {
  seq += 1;
  return `${prefix}_${seq}`;
}

export const EventTriggerPanel: React.FC<EventTriggerPanelProps> = ({
  settings,
  onTriggerEvent,
  onTriggerQueueBurst,
}) => {
  const triggerButtons: Array<{
    id: string;
    label: string;
    detail: string;
    icon: React.ReactNode;
    disabled: boolean;
    onClick: () => void;
  }> = [
    {
      id: 'charging-started',
      label: 'Charging Started',
      detail: '84% · Filling ring',
      icon: <Zap className="w-4 h-4 text-[#16A34A]" />,
      disabled: !settings.enabledEvents.CHARGING_STARTED,
      onClick: () =>
        onTriggerEvent({ id: nextId('chg_on'), type: 'CHARGING_STARTED', batteryPercent: 84 }),
    },
    {
      id: 'charging-stopped',
      label: 'Charging Stopped',
      detail: '84% · Unplugged',
      icon: <Battery className="w-4 h-4 text-[#475569]" />,
      disabled: !settings.enabledEvents.CHARGING_STOPPED,
      onClick: () =>
        onTriggerEvent({ id: nextId('chg_off'), type: 'CHARGING_STOPPED', batteryPercent: 84 }),
    },
    {
      id: 'battery-full',
      label: 'Battery Full',
      detail: '100% · Green check',
      icon: <CheckCircle2 className="w-4 h-4 text-[#16A34A]" />,
      disabled: !settings.enabledEvents.BATTERY_FULL,
      onClick: () =>
        onTriggerEvent({ id: nextId('bat_full'), type: 'BATTERY_FULL', batteryPercent: 100 }),
    },
    {
      id: 'low-battery-20',
      label: 'Low Battery (20%)',
      detail: 'Orange warning',
      icon: <BatteryWarning className="w-4 h-4 text-[#D97706]" />,
      disabled: !settings.enabledEvents.LOW_BATTERY,
      onClick: () =>
        onTriggerEvent({
          id: nextId('low_20'),
          type: 'LOW_BATTERY',
          batteryPercent: 20,
          isCritical: false,
        }),
    },
    {
      id: 'low-battery-10',
      label: 'Critical Battery (10%)',
      detail: 'Red warning',
      icon: <BatteryCharging className="w-4 h-4 text-[#DC2626]" />,
      disabled: !settings.enabledEvents.LOW_BATTERY,
      onClick: () =>
        onTriggerEvent({
          id: nextId('low_10'),
          type: 'LOW_BATTERY',
          batteryPercent: 10,
          isCritical: true,
        }),
    },
    {
      id: 'wifi-connected',
      label: 'Wi-Fi Connected',
      detail: 'Studio-5G',
      icon: <Wifi className="w-4 h-4 text-[#2563EB]" />,
      disabled: !settings.enabledEvents.WIFI_CONNECTED,
      onClick: () =>
        onTriggerEvent({ id: nextId('wifi_on'), type: 'WIFI_CONNECTED', ssid: 'Studio-5G' }),
    },
    {
      id: 'wifi-disconnected',
      label: 'Wi-Fi Disconnected',
      detail: 'Crossed-out icon',
      icon: <WifiOff className="w-4 h-4 text-[#DC2626]" />,
      disabled: !settings.enabledEvents.WIFI_DISCONNECTED,
      onClick: () => onTriggerEvent({ id: nextId('wifi_off'), type: 'WIFI_DISCONNECTED' }),
    },
    {
      id: 'hotspot-on',
      label: 'Hotspot On',
      detail: 'Tethering active',
      icon: <Radio className="w-4 h-4 text-[#16A34A]" />,
      disabled: !settings.enabledEvents.HOTSPOT,
      onClick: () => onTriggerEvent({ id: nextId('hs_on'), type: 'HOTSPOT', enabled: true }),
    },
    {
      id: 'hotspot-off',
      label: 'Hotspot Off',
      detail: 'Tethering off',
      icon: <Radio className="w-4 h-4 text-[#64748B]" />,
      disabled: !settings.enabledEvents.HOTSPOT,
      onClick: () => onTriggerEvent({ id: nextId('hs_off'), type: 'HOTSPOT', enabled: false }),
    },
    {
      id: 'bt-headphones',
      label: 'Bluetooth Audio',
      detail: 'Sony WH-1000XM5',
      icon: <Headphones className="w-4 h-4 text-[#0284C7]" />,
      disabled: !settings.enabledEvents.BLUETOOTH,
      onClick: () =>
        onTriggerEvent({
          id: nextId('bt_audio'),
          type: 'BLUETOOTH',
          deviceName: 'Sony WH-1000XM5',
          connected: true,
          isAudio: true,
          batteryLevel: 85,
        }),
    },
    {
      id: 'bt-disconnected',
      label: 'Bluetooth Off',
      detail: 'Device disconnected',
      icon: <BluetoothOff className="w-4 h-4 text-[#64748B]" />,
      disabled: !settings.enabledEvents.BLUETOOTH,
      onClick: () =>
        onTriggerEvent({
          id: nextId('bt_off'),
          type: 'BLUETOOTH',
          deviceName: 'Sony WH-1000XM5',
          connected: false,
          isAudio: true,
        }),
    },
    {
      id: 'bt-generic',
      label: 'Bluetooth Device',
      detail: 'Keychron K3',
      icon: <Bluetooth className="w-4 h-4 text-[#0284C7]" />,
      disabled: !settings.enabledEvents.BLUETOOTH,
      onClick: () =>
        onTriggerEvent({
          id: nextId('bt_kbd'),
          type: 'BLUETOOTH',
          deviceName: 'Keychron K3',
          connected: true,
          isAudio: false,
        }),
    },
    {
      id: 'airplane-on',
      label: 'Airplane Mode On',
      detail: 'Radios suspended',
      icon: <Plane className="w-4 h-4 text-[#D97706]" />,
      disabled: !settings.enabledEvents.AIRPLANE_MODE,
      onClick: () =>
        onTriggerEvent({ id: nextId('air_on'), type: 'AIRPLANE_MODE', enabled: true }),
    },
    {
      id: 'airplane-off',
      label: 'Airplane Mode Off',
      detail: 'Radios restored',
      icon: <Plane className="w-4 h-4 text-[#16A34A]" />,
      disabled: !settings.enabledEvents.AIRPLANE_MODE,
      onClick: () =>
        onTriggerEvent({ id: nextId('air_off'), type: 'AIRPLANE_MODE', enabled: false }),
    },
    {
      id: 'ringer-silent',
      label: 'Silent Mode',
      detail: 'Alerts muted',
      icon: <BellOff className="w-4 h-4 text-[#DC2626]" />,
      disabled: !settings.enabledEvents.RINGER_MODE,
      onClick: () =>
        onTriggerEvent({ id: nextId('rng_silent'), type: 'RINGER_MODE', mode: 'SILENT' }),
    },
    {
      id: 'ringer-vibrate',
      label: 'Vibrate Mode',
      detail: 'Haptics active',
      icon: <Vibrate className="w-4 h-4 text-[#D97706]" />,
      disabled: !settings.enabledEvents.RINGER_MODE,
      onClick: () =>
        onTriggerEvent({ id: nextId('rng_vib'), type: 'RINGER_MODE', mode: 'VIBRATE' }),
    },
    {
      id: 'ringer-ring',
      label: 'Ring Mode',
      detail: 'Sound active',
      icon: <Bell className="w-4 h-4 text-[#16A34A]" />,
      disabled: !settings.enabledEvents.RINGER_MODE,
      onClick: () =>
        onTriggerEvent({ id: nextId('rng_ring'), type: 'RINGER_MODE', mode: 'RING' }),
    },
    {
      id: 'incoming-call',
      label: 'Incoming Call',
      detail: 'Accept / Decline',
      icon: <PhoneIncoming className="w-4 h-4 text-[#16A34A]" />,
      disabled: !settings.enabledEvents.INCOMING_CALL,
      onClick: () =>
        onTriggerEvent({
          id: nextId('call'),
          type: 'INCOMING_CALL',
          callerName: 'Elena Vance',
          phoneNumber: '+1 (415) 555-0194',
        }),
    },
    {
      id: 'music-player',
      label: 'Music Player',
      detail: 'Compact equalizer',
      icon: <Music className="w-4 h-4 text-[#7C3AED]" />,
      disabled: !settings.enabledEvents.MUSIC_PLAYER,
      onClick: () =>
        onTriggerEvent({
          id: 'music_session',
          type: 'MUSIC_PLAYER',
          track: {
            title: 'Midnight Refraction',
            artist: 'Kiasmos',
            isPlaying: true,
            positionSec: 68,
            durationSec: 214,
          },
        }),
    },
  ];

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-[#0F172A]">Event Triggers</h2>
          <p className="text-sm text-[#64748B]">
            Fire any system event to preview spring morphing, icon pop, and auto-collapse timing.
          </p>
        </div>
        <button
          type="button"
          onClick={onTriggerQueueBurst}
          disabled={!settings.masterEnabled}
          className="inline-flex items-center gap-2 px-4 py-2 text-xs font-medium rounded-lg bg-[#0F172A] text-white hover:bg-[#1E293B] disabled:opacity-40 transition-colors whitespace-nowrap shrink-0"
        >
          <Layers className="w-3.5 h-3.5" />
          Queue 3 Events
        </button>
      </div>

      {!settings.masterEnabled && (
        <div className="rounded-xl border border-[#F59E0B]/40 bg-[#FFFBEB] px-4 py-3 text-xs text-[#92400E]">
          Master switch is currently off. Re-enable it in Settings to trigger Dynamic Island events.
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
        {triggerButtons.map((item) => (
          <button
            key={item.id}
            type="button"
            disabled={!settings.masterEnabled || item.disabled}
            onClick={item.onClick}
            className="group flex items-center gap-3 p-3 rounded-xl border border-[#E4E7EC] bg-white hover:border-[#CBD5E1] hover:bg-[#F8FAFC] disabled:opacity-40 disabled:cursor-not-allowed text-left transition-colors"
          >
            <div className="w-9 h-9 rounded-lg bg-[#F1F5F9] group-hover:bg-white border border-[#E2E8F0] flex items-center justify-center shrink-0 transition-colors">
              {item.icon}
            </div>
            <div className="min-w-0">
              <p className="text-sm font-medium text-[#0F172A] truncate">{item.label}</p>
              <p className="text-xs text-[#64748B] truncate">{item.detail}</p>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
};

export type IslandEventType =
  | 'CHARGING_STARTED'
  | 'CHARGING_STOPPED'
  | 'BATTERY_FULL'
  | 'LOW_BATTERY'
  | 'WIFI_CONNECTED'
  | 'WIFI_DISCONNECTED'
  | 'HOTSPOT'
  | 'BLUETOOTH'
  | 'AIRPLANE_MODE'
  | 'RINGER_MODE'
  | 'INCOMING_CALL'
  | 'MUSIC_PLAYER';

export type IslandDisplayMode = 'IDLE' | 'COMPACT' | 'EXPANDED' | 'FULL_EXPANDED';

export type DisplayDurationSeconds = 2 | 4 | 6;

export type RingerMode = 'SILENT' | 'VIBRATE' | 'RING';

export interface MusicTrack {
  title: string;
  artist: string;
  isPlaying: boolean;
  positionSec: number;
  durationSec: number;
}

export type IslandEvent =
  | { id: string; type: 'CHARGING_STARTED'; batteryPercent: number }
  | { id: string; type: 'CHARGING_STOPPED'; batteryPercent: number }
  | { id: string; type: 'BATTERY_FULL'; batteryPercent: number }
  | { id: string; type: 'LOW_BATTERY'; batteryPercent: number; isCritical: boolean }
  | { id: string; type: 'WIFI_CONNECTED'; ssid: string }
  | { id: string; type: 'WIFI_DISCONNECTED' }
  | { id: string; type: 'HOTSPOT'; enabled: boolean }
  | { id: string; type: 'BLUETOOTH'; deviceName: string; connected: boolean; isAudio: boolean; batteryLevel?: number }
  | { id: string; type: 'AIRPLANE_MODE'; enabled: boolean }
  | { id: string; type: 'RINGER_MODE'; mode: RingerMode }
  | { id: string; type: 'INCOMING_CALL'; callerName: string; phoneNumber: string }
  | { id: string; type: 'MUSIC_PLAYER'; track: MusicTrack }
  | { id: string; type: 'MUSIC_SESSION_ENDED' };

export type AppTransitionDirection = 'EXIT_TO_ISLAND' | 'ENTER_FROM_ISLAND';

export interface AppTransitionState {
  id: string;
  direction: AppTransitionDirection;
  title: string;
  artist: string;
  dominantColor: string;
}

export interface IslandSettings {
  masterEnabled: boolean;
  autoStartOnBoot: boolean;
  hideInFullscreen: boolean;
  bluetoothAudioOnly: boolean;
  appExitAnimationEnabled: boolean;
  animationSpeed: number;
  displayDurationSec: DisplayDurationSeconds;
  offsetX: number;
  offsetY: number;
  idleWidth: number;
  idleHeight: number;
  cornerRadius: number;
  enabledEvents: Record<IslandEventType, boolean>;
}

export interface IslandState {
  activeEvent: IslandEvent | null;
  queue: IslandEvent[];
  displayMode: IslandDisplayMode;
  persistentMusic: MusicTrack | null;
  isFullscreen: boolean;
  callStatusMessage: string | null;
  activeTransition: AppTransitionState | null;
}

export const EVENT_METADATA: Array<{
  type: IslandEventType;
  label: string;
  description: string;
}> = [
  { type: 'CHARGING_STARTED', label: 'Charging Started', description: 'Green bolt icon & filling battery ring' },
  { type: 'CHARGING_STOPPED', label: 'Charging Stopped', description: 'Unplugged battery percentage summary' },
  { type: 'BATTERY_FULL', label: 'Battery Full', description: '100% charged badge with green check' },
  { type: 'LOW_BATTERY', label: 'Low Battery', description: '20% orange & 10% red critical alerts' },
  { type: 'WIFI_CONNECTED', label: 'Wi-Fi Connected', description: 'Animated Wi-Fi icon & network SSID' },
  { type: 'WIFI_DISCONNECTED', label: 'Wi-Fi Disconnected', description: 'Crossed-out Wi-Fi alert' },
  { type: 'HOTSPOT', label: 'Hotspot On / Off', description: 'Personal tethering state indicator' },
  { type: 'BLUETOOTH', label: 'Bluetooth Devices', description: 'Device name & headphone audio icon' },
  { type: 'AIRPLANE_MODE', label: 'Airplane Mode', description: 'Wireless radio toggle card' },
  { type: 'RINGER_MODE', label: 'Silent / Vibrate / Ring', description: 'Audio ringer profile changes' },
  { type: 'INCOMING_CALL', label: 'Incoming Call', description: 'Caller card with Accept & Decline' },
  { type: 'MUSIC_PLAYER', label: 'Music Player', description: 'Compact equalizer pill & media controls' },
];

export function createDefaultSettings(): IslandSettings {
  return {
    masterEnabled: true,
    autoStartOnBoot: true,
    hideInFullscreen: true,
    bluetoothAudioOnly: true,
    appExitAnimationEnabled: true,
    animationSpeed: 1.0,
    displayDurationSec: 4,
    offsetX: 0,
    offsetY: 12,
    idleWidth: 126,
    idleHeight: 36,
    cornerRadius: 24,
    enabledEvents: {
      CHARGING_STARTED: true,
      CHARGING_STOPPED: true,
      BATTERY_FULL: true,
      LOW_BATTERY: true,
      WIFI_CONNECTED: true,
      WIFI_DISCONNECTED: true,
      HOTSPOT: true,
      BLUETOOTH: true,
      AIRPLANE_MODE: true,
      RINGER_MODE: true,
      INCOMING_CALL: true,
      MUSIC_PLAYER: true,
    },
  };
}

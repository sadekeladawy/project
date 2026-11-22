import React from 'react';
import { RotateCcw } from 'lucide-react';
import {
  DisplayDurationSeconds,
  EVENT_METADATA,
  IslandEventType,
  IslandSettings,
} from '../island/types';
import { DynamicIslandCapsule } from './DynamicIslandCapsule';

interface SettingsPanelProps {
  settings: IslandSettings;
  onUpdateSettings: (patch: Partial<IslandSettings>) => void;
  onToggleEventType: (type: IslandEventType, enabled: boolean) => void;
  onResetToDefault: () => void;
}

export const SettingsPanel: React.FC<SettingsPanelProps> = ({
  settings,
  onUpdateSettings,
  onToggleEventType,
  onResetToDefault,
}) => {
  const durations: DisplayDurationSeconds[] = [2, 4, 6];

  return (
    <div className="space-y-6">
      {/* Live Preview anchored at the top of Settings */}
      <div className="rounded-2xl border border-[#E4E7EC] bg-white overflow-hidden">
        <div className="px-5 py-3.5 border-b border-[#E4E7EC] flex items-center justify-between">
          <div>
            <h2 className="text-base font-semibold text-[#0F172A]">Live Island Preview</h2>
            <p className="text-xs text-[#64748B]">Updates immediately as position and size sliders move</p>
          </div>
          <button
            type="button"
            onClick={onResetToDefault}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-[#E4E7EC] bg-white text-[#0F172A] hover:bg-[#F8FAFC] transition-colors whitespace-nowrap shrink-0"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            Reset to default
          </button>
        </div>

        <div className="relative h-32 bg-[#0B0E14] flex justify-center overflow-hidden">
          <DynamicIslandCapsule
            activeEvent={null}
            displayMode="IDLE"
            queueCount={0}
            settings={settings}
            isHidden={!settings.masterEnabled}
            onTap={() => {}}
            onLongPress={() => {}}
            onSwipeUp={() => {}}
            onMediaAction={() => {}}
            onCallAction={() => {}}
          />
          <div className="absolute bottom-2.5 text-[11px] font-mono text-zinc-400 tabular-nums">
            {settings.idleWidth} × {settings.idleHeight} dp · radius {settings.cornerRadius} dp · offset ({settings.offsetX}, {settings.offsetY})
          </div>
        </div>
      </div>

      {/* Position & Size Sliders */}
      <div className="rounded-2xl border border-[#E4E7EC] bg-white p-5 space-y-4">
        <h3 className="text-base font-semibold text-[#0F172A]">Position &amp; Size</h3>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4">
          <SliderField
            id="slider-offset-y"
            label="Vertical position (Y offset)"
            value={settings.offsetY}
            min={4}
            max={64}
            unit="dp"
            onChange={(val) => onUpdateSettings({ offsetY: val })}
          />
          <SliderField
            id="slider-offset-x"
            label="Horizontal position (X offset)"
            value={settings.offsetX}
            min={-80}
            max={80}
            unit="dp"
            onChange={(val) => onUpdateSettings({ offsetX: val })}
          />
          <SliderField
            id="slider-width"
            label="Idle capsule width"
            value={settings.idleWidth}
            min={84}
            max={200}
            unit="dp"
            onChange={(val) => onUpdateSettings({ idleWidth: val })}
          />
          <SliderField
            id="slider-height"
            label="Idle capsule height"
            value={settings.idleHeight}
            min={24}
            max={50}
            unit="dp"
            onChange={(val) => onUpdateSettings({ idleHeight: val })}
          />
          <SliderField
            id="slider-radius"
            label="Corner radius"
            value={settings.cornerRadius}
            min={10}
            max={32}
            unit="dp"
            onChange={(val) => onUpdateSettings({ cornerRadius: val })}
          />
        </div>
      </div>

      {/* General & Behavior Settings */}
      <div className="rounded-2xl border border-[#E4E7EC] bg-white p-5 space-y-4">
        <h3 className="text-base font-semibold text-[#0F172A]">Behavior</h3>

        <div className="space-y-3 divide-y divide-[#F1F5F9]">
          <ToggleRow
            id="toggle-master"
            label="Master on/off switch"
            description="Enable or disable the floating Dynamic Island overlay service"
            checked={settings.masterEnabled}
            onChange={(checked) => onUpdateSettings({ masterEnabled: checked })}
          />
          <ToggleRow
            id="toggle-boot"
            label="Auto-start on boot"
            description="Automatically start the foreground overlay service when Android boots"
            checked={settings.autoStartOnBoot}
            onChange={(checked) => onUpdateSettings({ autoStartOnBoot: checked })}
          />
          <ToggleRow
            id="toggle-fullscreen"
            label="Hide in fullscreen"
            description="Fade out smoothly when watching videos or playing fullscreen games"
            checked={settings.hideInFullscreen}
            onChange={(checked) => onUpdateSettings({ hideInFullscreen: checked })}
          />
          <ToggleRow
            id="toggle-bt-audio-only"
            label="Audio devices only"
            description="Only show Bluetooth cards for headphones, earbuds, and speakers"
            checked={settings.bluetoothAudioOnly}
            onChange={(checked) => onUpdateSettings({ bluetoothAudioOnly: checked })}
          />
          <ToggleRow
            id="toggle-app-exit"
            label="App exit animation"
            description="Morph Spotify / YouTube Music into the island when leaving the app (Accessibility or Usage Access)"
            checked={settings.appExitAnimationEnabled}
            onChange={(checked) => onUpdateSettings({ appExitAnimationEnabled: checked })}
          />
        </div>

        <div className="pt-2 space-y-4">
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs">
              <label htmlFor="slider-animation-speed" className="font-medium text-[#0F172A]">
                Animation speed
              </label>
              <span className="font-mono font-medium text-[#2563EB] tabular-nums">
                {settings.animationSpeed.toFixed(2)}x
              </span>
            </div>
            <input
              id="slider-animation-speed"
              type="range"
              min={0.5}
              max={2.0}
              step={0.05}
              value={settings.animationSpeed}
              onChange={(e) => onUpdateSettings({ animationSpeed: Number(e.target.value) })}
              className="w-full h-1.5 bg-[#E2E8F0] rounded-lg appearance-none cursor-pointer accent-[#2563EB]"
            />
          </div>

          <div>
            <p className="text-sm font-medium text-[#0F172A] mb-2">Card visible duration</p>
            <div className="inline-flex p-1 rounded-lg bg-[#F1F5F9] border border-[#E2E8F0]">
              {durations.map((sec) => (
                <button
                  key={sec}
                  type="button"
                  onClick={() => onUpdateSettings({ displayDurationSec: sec })}
                  className={`px-4 py-1.5 text-xs font-medium rounded-md transition-colors tabular-nums ${
                    settings.displayDurationSec === sec
                      ? 'bg-white text-[#0F172A] shadow-sm'
                      : 'text-[#64748B] hover:text-[#0F172A]'
                  }`}
                >
                  {sec}s
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Per-event Toggles */}
      <div className="rounded-2xl border border-[#E4E7EC] bg-white p-5 space-y-3">
        <div>
          <h3 className="text-base font-semibold text-[#0F172A]">Event Categories</h3>
          <p className="text-xs text-[#64748B]">Choose which system events animate the Dynamic Island</p>
        </div>

        <div className="divide-y divide-[#F1F5F9]">
          {EVENT_METADATA.map((item) => (
            <ToggleRow
              key={item.type}
              id={`toggle-event-${item.type.toLowerCase()}`}
              label={item.label}
              description={item.description}
              checked={settings.enabledEvents[item.type]}
              onChange={(checked) => onToggleEventType(item.type, checked)}
            />
          ))}
        </div>
      </div>
    </div>
  );
};

function SliderField({
  id,
  label,
  value,
  min,
  max,
  unit,
  onChange,
}: {
  id: string;
  label: string;
  value: number;
  min: number;
  max: number;
  unit: string;
  onChange: (value: number) => void;
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs">
        <label htmlFor={id} className="font-medium text-[#0F172A]">
          {label}
        </label>
        <span className="font-mono font-medium text-[#2563EB] tabular-nums">
          {value} {unit}
        </span>
      </div>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full h-1.5 bg-[#E2E8F0] rounded-lg appearance-none cursor-pointer accent-[#2563EB]"
      />
    </div>
  );
}

function ToggleRow({
  id,
  label,
  description,
  checked,
  onChange,
}: {
  id: string;
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <div className="py-3 first:pt-1 last:pb-1 flex items-center justify-between gap-4">
      <label htmlFor={id} className="cursor-pointer flex-1">
        <p className="text-sm font-medium text-[#0F172A]">{label}</p>
        <p className="text-xs text-[#64748B]">{description}</p>
      </label>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors focus-visible:outline-2 focus-visible:outline-[#2563EB] ${
          checked ? 'bg-[#2563EB]' : 'bg-[#CBD5E1]'
        }`}
      >
        <span
          className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm transition-transform ${
            checked ? 'translate-x-5' : 'translate-x-0'
          }`}
        />
      </button>
    </div>
  );
}

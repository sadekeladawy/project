import test from 'node:test';
import assert from 'node:assert/strict';
import {
  advanceOrCollapseIsland,
  clampSettings,
  collapseIsland,
  createInitialIslandState,
  enqueueIslandEvent,
  finishAppTransition,
  getIslandDimensions,
  getIslandVerticalOffset,
  longPressIsland,
  onExitAnimationReachedIsland,
  resetPositionAndSize,
  resolveIncomingCall,
  seekMusic,
  startAppTransition,
  tapIsland,
  toggleMusicPlayPause,
} from './islandReducer';
import { createDefaultSettings } from './types';

test('queues incoming transient events when another non-music card is active and swaps cleanly', () => {
  const settings = createDefaultSettings();
  let state = createInitialIslandState();

  state = enqueueIslandEvent(state, { id: '1', type: 'CHARGING_STARTED', batteryPercent: 72 }, settings);
  assert.equal(state.displayMode, 'COMPACT');
  assert.equal(state.activeEvent?.type, 'CHARGING_STARTED');
  assert.equal(state.queue.length, 0);

  state = enqueueIslandEvent(state, { id: '2', type: 'WIFI_CONNECTED', ssid: 'Studio-5G' }, settings);
  assert.equal(state.activeEvent?.type, 'CHARGING_STARTED');
  assert.equal(state.queue.length, 1);

  state = advanceOrCollapseIsland(state);
  assert.equal(state.activeEvent?.type, 'WIFI_CONNECTED');
  assert.equal(state.queue.length, 0);

  state = advanceOrCollapseIsland(state);
  assert.equal(state.activeEvent, null);
  assert.equal(state.displayMode, 'IDLE');
});

test('music playback persists in compact mode after transient notifications finish', () => {
  const settings = createDefaultSettings();
  let state = createInitialIslandState();

  state = enqueueIslandEvent(
    state,
    {
      id: 'm1',
      type: 'MUSIC_PLAYER',
      track: { title: 'Midnight Refraction', artist: 'Kiasmos', isPlaying: true, positionSec: 40, durationSec: 210 },
    },
    settings
  );
  assert.equal(state.displayMode, 'COMPACT');
  assert.equal(state.activeEvent?.type, 'MUSIC_PLAYER');

  state = enqueueIslandEvent(
    state,
    { id: 'b1', type: 'BLUETOOTH', deviceName: 'Sony WH-1000XM5', connected: true, isAudio: true },
    settings
  );
  assert.equal(state.activeEvent?.type, 'BLUETOOTH');

  state = advanceOrCollapseIsland(state);
  assert.equal(state.activeEvent?.type, 'MUSIC_PLAYER');
  assert.equal(state.displayMode, 'COMPACT');
});

test('tap expands compact card, long press opens full expanded, collapse returns to idle', () => {
  const settings = createDefaultSettings();
  let state = createInitialIslandState();

  state = enqueueIslandEvent(state, { id: '1', type: 'BATTERY_FULL', batteryPercent: 100 }, settings);
  state = tapIsland(state);
  assert.equal(state.displayMode, 'EXPANDED');

  state = longPressIsland(state);
  assert.equal(state.displayMode, 'FULL_EXPANDED');

  state = collapseIsland(state);
  assert.equal(state.displayMode, 'IDLE');
  assert.equal(state.activeEvent, null);
});

test('disabled events and master off are respected', () => {
  const settings = createDefaultSettings();
  settings.enabledEvents.HOTSPOT = false;

  let state = createInitialIslandState();
  state = enqueueIslandEvent(state, { id: 'h1', type: 'HOTSPOT', enabled: true }, settings);
  assert.equal(state.activeEvent, null);

  settings.enabledEvents.HOTSPOT = true;
  settings.masterEnabled = false;
  state = enqueueIslandEvent(state, { id: 'h2', type: 'HOTSPOT', enabled: true }, settings);
  assert.equal(state.activeEvent, null);
});

test('incoming call preempts active events and resolves cleanly', () => {
  const settings = createDefaultSettings();
  let state = createInitialIslandState();
  state = enqueueIslandEvent(state, { id: '1', type: 'CHARGING_STARTED', batteryPercent: 50 }, settings);
  state = enqueueIslandEvent(
    state,
    { id: 'c1', type: 'INCOMING_CALL', callerName: 'Elena Vance', phoneNumber: '+1 415 555 0194' },
    settings
  );
  assert.equal(state.activeEvent?.type, 'INCOMING_CALL');
  assert.equal(state.displayMode, 'EXPANDED');

  state = resolveIncomingCall(state, 'ACCEPT');
  assert.equal(state.displayMode, 'IDLE');
  assert.equal(state.callStatusMessage, 'Connected with Elena Vance');
});

test('settings clamping, resetToDefault, music toggle, seekMusic, and dimensions behave deterministically', () => {
  const base = createDefaultSettings();
  const modified = clampSettings(
    { offsetX: 999, offsetY: -10, idleWidth: 500, cornerRadius: 5, animationSpeed: 4.5 },
    base
  );
  assert.equal(modified.offsetX, 80);
  assert.equal(modified.offsetY, 4);
  assert.equal(modified.idleWidth, 200);
  assert.equal(modified.cornerRadius, 10);
  assert.equal(modified.animationSpeed, 2.0);

  const slow = clampSettings({ animationSpeed: 0.1 }, base);
  assert.equal(slow.animationSpeed, 0.5);

  const reset = resetPositionAndSize(modified);
  assert.equal(reset.offsetX, base.offsetX);
  assert.equal(reset.offsetY, base.offsetY);
  assert.equal(reset.idleWidth, base.idleWidth);

  const dims = getIslandDimensions('IDLE', null, reset);
  assert.deepEqual(dims, { width: 126, height: 36, radius: 24 });

  let state = createInitialIslandState();
  state = enqueueIslandEvent(
    state,
    {
      id: 'm1',
      type: 'MUSIC_PLAYER',
      track: { title: 'Echoes', artist: 'Rival Consoles', isPlaying: true, positionSec: 12, durationSec: 180 },
    },
    base
  );
  state = seekMusic(state, 125);
  assert.equal(state.persistentMusic?.positionSec, 125);
  assert.equal(state.activeEvent?.type === 'MUSIC_PLAYER' ? state.activeEvent.track.positionSec : -1, 125);

  state = seekMusic(state, 999);
  assert.equal(state.persistentMusic?.positionSec, 180);

  state = toggleMusicPlayPause(state);
  assert.equal(state.persistentMusic?.isPlaying, false);
});

test('app exit and reverse enter transitions respect toggle and expand/finish cleanly', () => {
  const settings = createDefaultSettings();
  let state = createInitialIslandState();

  state = enqueueIslandEvent(
    state,
    {
      id: 'music_session',
      type: 'MUSIC_PLAYER',
      track: { title: 'Midnight Refraction', artist: 'Kiasmos', isPlaying: true, positionSec: 45, durationSec: 214 },
    },
    settings
  );

  // Disabled toggle prevents transition
  const disabled = startAppTransition(state, 'EXIT_TO_ISLAND', { ...settings, appExitAnimationEnabled: false }, 'tx-0');
  assert.equal(disabled.activeTransition, null);

  // Exit transition starts and expands when reaching island
  state = startAppTransition(state, 'EXIT_TO_ISLAND', settings, 'tx-1', '#2E1065');
  assert.equal(state.activeTransition?.direction, 'EXIT_TO_ISLAND');

  state = onExitAnimationReachedIsland(state);
  assert.equal(state.displayMode, 'EXPANDED');
  assert.equal(state.activeEvent?.type, 'MUSIC_PLAYER');

  state = finishAppTransition(state, 'tx-1');
  assert.equal(state.activeTransition, null);

  // Reverse enter collapses to compact while card grows out of island
  state = startAppTransition(state, 'ENTER_FROM_ISLAND', settings, 'tx-2', '#2E1065');
  assert.equal(state.displayMode, 'COMPACT');
  assert.equal(state.activeTransition?.direction, 'ENTER_FROM_ISLAND');

  state = finishAppTransition(state, 'tx-2');
  assert.equal(state.activeTransition, null);
});

test('pausing music while expanded replaces activeEvent with isPlaying=false', () => {
  const settings = createDefaultSettings();
  let state = createInitialIslandState();

  state = enqueueIslandEvent(
    state,
    {
      id: 'm1',
      type: 'MUSIC_PLAYER',
      track: { title: 'Midnight Refraction', artist: 'Kiasmos', isPlaying: true, positionSec: 42, durationSec: 214 },
    },
    settings
  );
  state = tapIsland(state);
  assert.equal(state.displayMode, 'EXPANDED');
  assert.equal(state.activeEvent?.type === 'MUSIC_PLAYER' && state.activeEvent.track.isPlaying, true);

  state = enqueueIslandEvent(
    state,
    {
      id: 'm2',
      type: 'MUSIC_PLAYER',
      track: { title: 'Midnight Refraction', artist: 'Kiasmos', isPlaying: false, positionSec: 45, durationSec: 214 },
    },
    settings
  );
  assert.equal(state.displayMode, 'EXPANDED');
  assert.equal(state.activeEvent?.type, 'MUSIC_PLAYER');
  assert.equal(state.activeEvent?.type === 'MUSIC_PLAYER' ? state.activeEvent.track.isPlaying : true, false);
  assert.equal(state.persistentMusic?.isPlaying, false);
});

test('ending music session or blank track clears persistentMusic and prevents empty expansion', () => {
  const settings = createDefaultSettings();
  let state = createInitialIslandState();

  state = enqueueIslandEvent(
    state,
    {
      id: 'm1',
      type: 'MUSIC_PLAYER',
      track: { title: 'Tides of Glass', artist: 'Rival Consoles', isPlaying: true, positionSec: 12, durationSec: 196 },
    },
    settings
  );
  assert.equal(state.displayMode, 'COMPACT');

  // Explicit MUSIC_SESSION_ENDED clears persistentMusic and returns to IDLE
  state = enqueueIslandEvent(state, { id: 'end-1', type: 'MUSIC_SESSION_ENDED' }, settings);
  assert.equal(state.persistentMusic, null);
  assert.equal(state.activeEvent, null);
  assert.equal(state.displayMode, 'IDLE');

  // Tap and long-press do not expand empty music
  state = tapIsland(state);
  assert.equal(state.displayMode, 'IDLE');
  assert.equal(state.activeEvent, null);

  state = longPressIsland(state);
  assert.equal(state.displayMode, 'IDLE');
  assert.equal(state.activeEvent, null);

  // Blank title music event also clears session instead of creating an empty card
  state = enqueueIslandEvent(
    state,
    {
      id: 'm2',
      type: 'MUSIC_PLAYER',
      track: { title: '   ', artist: '', isPlaying: false, positionSec: 0, durationSec: 0 },
    },
    settings
  );
  assert.equal(state.persistentMusic, null);
  assert.equal(state.activeEvent, null);
  assert.equal(state.displayMode, 'IDLE');
});

test('queue replaces duplicate event types instead of stacking duplicates and filters non-audio Bluetooth', () => {
  const settings = createDefaultSettings();
  let state = createInitialIslandState();

  // Active event occupies capsule
  state = enqueueIslandEvent(state, { id: 'c1', type: 'CHARGING_STARTED', batteryPercent: 50 }, settings);
  assert.equal(state.activeEvent?.type, 'CHARGING_STARTED');

  // Queue two Wi-Fi events and a Ringer event; the second Wi-Fi event should replace the first
  state = enqueueIslandEvent(state, { id: 'w1', type: 'WIFI_CONNECTED', ssid: 'Home-2.4G' }, settings);
  state = enqueueIslandEvent(state, { id: 'r1', type: 'RINGER_MODE', mode: 'VIBRATE' }, settings);
  state = enqueueIslandEvent(state, { id: 'w2', type: 'WIFI_CONNECTED', ssid: 'Home-5G' }, settings);

  assert.equal(state.queue.length, 2);
  assert.equal(state.queue[0]?.type, 'WIFI_CONNECTED');
  assert.equal(state.queue[0]?.type === 'WIFI_CONNECTED' ? state.queue[0].ssid : '', 'Home-5G');
  assert.equal(state.queue[1]?.type, 'RINGER_MODE');

  // Default bluetoothAudioOnly=true ignores non-audio Bluetooth devices
  let btState = createInitialIslandState();
  btState = enqueueIslandEvent(
    btState,
    { id: 'bt1', type: 'BLUETOOTH', deviceName: 'Smart Tracker', connected: true, isAudio: false },
    settings
  );
  assert.equal(btState.activeEvent, null);

  // Audio Bluetooth device still triggers card
  btState = enqueueIslandEvent(
    btState,
    { id: 'bt2', type: 'BLUETOOTH', deviceName: 'Sony WH-1000XM5', connected: true, isAudio: true },
    settings
  );
  assert.equal(btState.activeEvent?.type, 'BLUETOOTH');

  // When bluetoothAudioOnly=false, non-audio devices are allowed
  const allowAllSettings = { ...settings, bluetoothAudioOnly: false };
  let allBtState = createInitialIslandState();
  allBtState = enqueueIslandEvent(
    allBtState,
    { id: 'bt3', type: 'BLUETOOTH', deviceName: 'Smart Watch', connected: true, isAudio: false },
    allowAllSettings
  );
  assert.equal(allBtState.activeEvent?.type, 'BLUETOOTH');
});

test('proportional expansion bounds keep status bar and camera punch-hole unobstructed', () => {
  const settings = createDefaultSettings();
  const musicEvent = {
    id: 'music_session',
    type: 'MUSIC_PLAYER' as const,
    track: { title: 'Midnight Refraction', artist: 'Kiasmos', isPlaying: true, positionSec: 42, durationSec: 214 },
  };
  const callEvent = {
    id: 'call-1',
    type: 'INCOMING_CALL' as const,
    callerName: 'Elena Vance',
    phoneNumber: '+1 415 555 0194',
  };
  const wifiEvent = {
    id: 'wifi-1',
    type: 'WIFI_CONNECTED' as const,
    ssid: 'Studio-5G',
  };

  // Compact widths stay proportionally capped (<= 212) so left/right status bar notifications remain clear
  for (const evt of [musicEvent, callEvent, wifiEvent]) {
    const compact = getIslandDimensions('COMPACT', evt, settings);
    assert.ok(compact.width <= 212, `compact width ${compact.width} should be <= 212`);
    assert.ok(compact.height <= 38, `compact height ${compact.height} should be <= 38`);
    assert.equal(getIslandVerticalOffset('COMPACT', evt, settings), settings.offsetY);
  }

  // Expanded & Full-Expanded stay proportionally bounded and glide below the status bar / punch-hole (>= 42)
  const expMusic = getIslandDimensions('EXPANDED', musicEvent, settings);
  assert.ok(expMusic.width <= 308 && expMusic.height <= 152);
  assert.ok(getIslandVerticalOffset('EXPANDED', musicEvent, settings) >= 42);

  const fullMusic = getIslandDimensions('FULL_EXPANDED', musicEvent, settings);
  assert.ok(fullMusic.width <= 322 && fullMusic.height <= 176);
  assert.ok(getIslandVerticalOffset('FULL_EXPANDED', musicEvent, settings) >= 42);

  const expWifi = getIslandDimensions('EXPANDED', wifiEvent, settings);
  assert.ok(expWifi.width <= 308 && expWifi.height <= 74);
});

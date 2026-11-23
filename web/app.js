import { DEFAULT_PARAMS, PRESETS } from './js/presets.js';
import { WebAudioEngine } from './js/audio-engine.js';

const STORAGE_KEY = 'haramain_web_params_v1';
const THEME_KEY = 'haramain_web_theme_v1';

const engine = new WebAudioEngine();
let params = loadSavedParams();
let deferredInstallPrompt = null;

const SLIDER_GROUPS = {
  'sliders-input': [
    { key: 'micBoostPercent', label: 'Mic Boost', min: 0, max: 200, step: 1, unit: '%' },
    { key: 'staticRemover', label: 'Static Remover (85 Hz HPF + Hiss)', min: 0, max: 100, step: 1, unit: '%' },
    { key: 'gateThresholdDb', label: 'Noise Gate Threshold', min: -80, max: 0, step: 1, unit: ' dB' },
    { key: 'noiseSuppressOn', label: 'Noise Suppression', type: 'toggle' },
    { key: 'noiseSuppress', label: 'Voice Isolation Depth', min: 0, max: 100, step: 1, unit: '%' },
  ],
  'sliders-dynamics': [
    { key: 'deEsserPercent', label: 'De-Esser (Sibilance)', min: 0, max: 100, step: 1, unit: '%' },
    { key: 'compressorPercent', label: 'Vocal Compressor', min: 0, max: 100, step: 1, unit: '%' },
  ],
  'sliders-eq': [
    { key: 'eqWarmthDb', label: 'Warmth (150 Hz)', min: -12, max: 12, step: 0.5, unit: ' dB' },
    { key: 'eqClarityDb', label: 'Clarity (3 kHz)', min: -12, max: 12, step: 0.5, unit: ' dB' },
    { key: 'eqAirDb', label: 'Air (10 kHz)', min: -12, max: 12, step: 0.5, unit: ' dB' },
    { key: 'eqLowGainDb', label: 'Bass Shelf Trim', min: -12, max: 12, step: 0.5, unit: ' dB' },
    { key: 'eqHighGainDb', label: 'Treble Shelf Trim', min: -12, max: 12, step: 0.5, unit: ' dB' },
    { key: 'eqBand60Db', label: 'GEQ 60 Hz', min: -12, max: 12, step: 0.5, unit: ' dB' },
    { key: 'eqBand250Db', label: 'GEQ 250 Hz', min: -12, max: 12, step: 0.5, unit: ' dB' },
    { key: 'eqBand1kDb', label: 'GEQ 1 kHz', min: -12, max: 12, step: 0.5, unit: ' dB' },
    { key: 'eqBand4kDb', label: 'GEQ 4 kHz', min: -12, max: 12, step: 0.5, unit: ' dB' },
    { key: 'eqBand12kDb', label: 'GEQ 12 kHz', min: -12, max: 12, step: 0.5, unit: ' dB' },
  ],
  'sliders-reverb': [
    { key: 'roomSizePercent', label: 'Room Size', min: 0, max: 100, step: 1, unit: '%' },
    { key: 'wetDryPercent', label: 'Reverb Mix (Wet/Dry)', min: 0, max: 100, step: 1, unit: '%' },
    { key: 'revDampingPercent', label: 'HF Damping', min: 0, max: 100, step: 1, unit: '%' },
    { key: 'revWidthPercent', label: 'Stereo Width', min: 0, max: 100, step: 1, unit: '%' },
  ],
  'sliders-safety': [
    { key: 'masterGainDb', label: 'Master Output Trim', min: -24, max: 12, step: 0.5, unit: ' dB' },
    { key: 'balancePercent', label: 'Stereo Balance', min: -100, max: 100, step: 1, unit: '%' },
    { key: 'safetyOn', label: 'Headphone Safety Ceiling', type: 'toggle' },
    { key: 'safetyCeilingDb', label: 'Safety Ceiling', min: -12, max: 0, step: 0.5, unit: ' dB' },
    { key: 'howlGuardOn', label: 'Anti-Howl Feedback Guard', type: 'toggle' },
    { key: 'btDuckOn', label: 'Bluetooth Dry Ducking', type: 'toggle' },
    { key: 'btDuckPercent', label: 'Dry Duck Amount', min: 0, max: 100, step: 1, unit: '%' },
  ],
};

function loadSavedParams() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_PARAMS };
    return { ...DEFAULT_PARAMS, ...JSON.parse(raw), muted: 0, bypass: 0 };
  } catch {
    return { ...DEFAULT_PARAMS };
  }
}

function saveParams() {
  try {
    const toSave = { ...params, muted: 0, bypass: 0 };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(toSave));
  } catch {}
}

function showError(msg) {
  const el = document.getElementById('errorBanner');
  if (!msg) {
    el.classList.add('hidden');
    el.textContent = '';
    return;
  }
  el.textContent = msg;
  el.classList.remove('hidden');
}

function dbFromLinear(peak) {
  if (!peak || peak < 1e-5) return -100;
  return 20 * Math.log10(peak);
}

function pctFromDb(db) {
  return Math.max(0, Math.min(100, ((db + 60) / 60) * 100));
}

function initTabs() {
  const tabs = document.querySelectorAll('.nav-btn');
  const panels = document.querySelectorAll('.panel');
  tabs.forEach((btn) => {
    btn.addEventListener('click', () => {
      tabs.forEach((t) => {
        t.classList.remove('active');
        t.setAttribute('aria-selected', 'false');
      });
      panels.forEach((p) => p.classList.remove('active'));
      btn.classList.add('active');
      btn.setAttribute('aria-selected', 'true');
      document.getElementById(btn.getAttribute('aria-controls')).classList.add('active');
    });
  });
}

function renderPresets() {
  const grid = document.getElementById('presetGrid');
  grid.innerHTML = '';
  const active = detectActivePreset();
  document.getElementById('activePresetLabel').textContent = active ? active.label : 'Custom';
  document.getElementById('presetDesc').textContent = active ? active.desc : 'Custom user tuning.';

  PRESETS.forEach((preset) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'preset-chip' + (active?.id === preset.id ? ' active' : '');
    btn.textContent = preset.label;
    btn.addEventListener('click', () => {
      params = {
        ...preset.values,
        muted: params.muted,
        bypass: params.bypass,
        monitorMode: params.monitorMode,
      };
      saveParams();
      engine.sendAllParams(params);
      syncAllControls();
      renderPresets();
    });
    grid.appendChild(btn);
  });
}

function detectActivePreset() {
  const keys = ['micBoostPercent', 'staticRemover', 'noiseSuppress', 'gateThresholdDb',
    'deEsserPercent', 'compressorPercent', 'roomSizePercent', 'wetDryPercent'];
  return PRESETS.find((p) => keys.every((k) => Math.abs((params[k] ?? 0) - p.values[k]) < 0.01)) || null;
}

function renderSliders() {
  for (const [containerId, items] of Object.entries(SLIDER_GROUPS)) {
    const root = document.getElementById(containerId);
    root.innerHTML = '';
    for (const item of items) {
      if (item.type === 'toggle') {
        const label = document.createElement('label');
        label.className = 'switch-row';
        label.innerHTML = `<span>${item.label}</span><input type="checkbox" data-param="${item.key}" />`;
        const input = label.querySelector('input');
        input.checked = Boolean(params[item.key]);
        input.addEventListener('change', () => {
          params[item.key] = input.checked ? 1 : 0;
          engine.setParam(item.key, params[item.key]);
          saveParams();
        });
        root.appendChild(label);
      } else {
        const wrap = document.createElement('div');
        wrap.className = 'slider-item';
        wrap.innerHTML = `
          <div class="slider-top">
            <label for="ctrl-${item.key}">${item.label}</label>
            <span id="val-${item.key}"></span>
          </div>
          <input id="ctrl-${item.key}" type="range" min="${item.min}" max="${item.max}" step="${item.step}" data-param="${item.key}" />
        `;
        const range = wrap.querySelector('input');
        const valEl = wrap.querySelector(`#val-${item.key}`);
        range.value = params[item.key];
        valEl.textContent = `${params[item.key]}${item.unit}`;
        range.addEventListener('input', () => {
          const val = parseFloat(range.value);
          params[item.key] = val;
          valEl.textContent = `${val}${item.unit}`;
          engine.setParam(item.key, val);
          saveParams();
          renderPresets();
        });
        root.appendChild(wrap);
      }
    }
  }
}

function syncAllControls() {
  for (const items of Object.values(SLIDER_GROUPS)) {
    for (const item of items) {
      const el = document.querySelector(`[data-param="${item.key}"]`);
      if (!el) continue;
      if (item.type === 'toggle') {
        el.checked = Boolean(params[item.key]);
      } else {
        el.value = params[item.key];
        const valEl = document.getElementById(`val-${item.key}`);
        if (valEl) valEl.textContent = `${params[item.key]}${item.unit}`;
      }
    }
  }
  document.querySelectorAll('[data-ears]').forEach((b) => {
    b.classList.toggle('active', Number(b.dataset.ears) === params.monitorMode);
  });
}

async function refreshDevices() {
  const { inputs, outputs } = await engine.listDevices();
  const inSel = document.getElementById('inputSelect');
  const outSel = document.getElementById('outputSelect');
  const prevIn = inSel.value;
  const prevOut = outSel.value;

  inSel.innerHTML = '<option value="">System Default</option>';
  inputs.forEach((d, i) => {
    const opt = document.createElement('option');
    opt.value = d.deviceId;
    opt.textContent = d.label || `Microphone ${i + 1}`;
    inSel.appendChild(opt);
  });
  inSel.value = prevIn;

  outSel.innerHTML = '<option value="">System Default</option>';
  outputs.forEach((d, i) => {
    const opt = document.createElement('option');
    opt.value = d.deviceId;
    opt.textContent = d.label || `Output ${i + 1}`;
    outSel.appendChild(opt);
  });
  outSel.value = prevOut;

  const sinkHint = document.getElementById('sinkHint');
  sinkHint.textContent = engine.supportsSinkSelection()
    ? 'Output routing supported in this browser.'
    : 'This browser routes audio via OS/Control Center (setSinkId unsupported).';
}

function initControls() {
  const toggleBtn = document.getElementById('toggleMonitorBtn');
  const toggleLabel = document.getElementById('toggleMonitorLabel');
  const recBtn = document.getElementById('recordBtn');
  const recStatus = document.getElementById('recordStatus');

  toggleBtn.addEventListener('click', async () => {
    showError('');
    if (!engine.running) {
      try {
        await engine.start({
          inputDeviceId: document.getElementById('inputSelect').value,
          outputDeviceId: document.getElementById('outputSelect').value,
          params,
        });
        toggleBtn.setAttribute('aria-pressed', 'true');
        toggleLabel.textContent = 'Stop Monitoring';
        recBtn.disabled = false;
        recStatus.textContent = 'Ready to record WAV';
        await refreshDevices();
      } catch (err) {
        showError(`Could not start audio: ${err.message || err}`);
      }
    } else {
      engine.stop();
      toggleBtn.setAttribute('aria-pressed', 'false');
      toggleLabel.textContent = 'Start Monitoring';
      recBtn.disabled = true;
      recBtn.textContent = 'Start Recording';
      recBtn.setAttribute('aria-pressed', 'false');
      recStatus.textContent = 'Start monitoring to record';
    }
  });

  const muteBtn = document.getElementById('muteBtn');
  muteBtn.addEventListener('click', () => {
    params.muted = params.muted ? 0 : 1;
    muteBtn.setAttribute('aria-pressed', String(Boolean(params.muted)));
    engine.setParam('muted', params.muted);
  });

  const bypassBtn = document.getElementById('bypassBtn');
  bypassBtn.addEventListener('click', () => {
    params.bypass = params.bypass ? 0 : 1;
    bypassBtn.setAttribute('aria-pressed', String(Boolean(params.bypass)));
    engine.setParam('bypass', params.bypass);
  });

  document.querySelectorAll('[data-ears]').forEach((btn) => {
    btn.addEventListener('click', () => {
      params.monitorMode = Number(btn.dataset.ears);
      engine.setParam('monitorMode', params.monitorMode);
      saveParams();
      syncAllControls();
    });
  });

  recBtn.addEventListener('click', () => {
    if (!engine.recording) {
      const dry = document.getElementById('recordDryToggle').checked;
      engine.startRecording(dry);
      recBtn.setAttribute('aria-pressed', 'true');
      recBtn.textContent = 'Stop & Save WAV';
      recStatus.textContent = dry ? 'Recording dry vocal...' : 'Recording processed audio...';
    } else {
      const blob = engine.stopRecording();
      recBtn.setAttribute('aria-pressed', 'false');
      recBtn.textContent = 'Start Recording';
      recStatus.textContent = 'Saved WAV below';
      if (blob) {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `haramain-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.wav`;
        a.className = 'download-link';
        a.textContent = `Download ${a.download} (${Math.round(blob.size / 1024)} KB)`;
        const area = document.getElementById('downloadArea');
        area.classList.remove('hidden');
        area.prepend(a);
      }
    }
  });

  document.getElementById('refreshDevicesBtn').addEventListener('click', refreshDevices);
  document.getElementById('outputSelect').addEventListener('change', (e) => {
    engine.setOutputDevice(e.target.value).catch((err) => showError(String(err)));
  });

  document.querySelectorAll('[data-palette]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const theme = btn.dataset.palette;
      document.documentElement.setAttribute('data-theme', theme);
      localStorage.setItem(THEME_KEY, theme);
      document.querySelectorAll('[data-palette]').forEach((b) =>
        b.classList.toggle('active', b.dataset.palette === theme)
      );
    });
  });

  document.getElementById('exportJsonBtn').addEventListener('click', () => {
    const blob = new Blob([JSON.stringify(params, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'haramain-monitor-settings.json';
    a.click();
    URL.revokeObjectURL(url);
  });

  document.getElementById('importJsonInput').addEventListener('change', async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const parsed = JSON.parse(await file.text());
      params = { ...DEFAULT_PARAMS, ...parsed, muted: 0, bypass: 0 };
      saveParams();
      engine.sendAllParams(params);
      syncAllControls();
      renderPresets();
    } catch (err) {
      showError(`Invalid JSON file: ${err.message}`);
    }
  });

  document.getElementById('resetAllBtn').addEventListener('click', () => {
    params = { ...DEFAULT_PARAMS };
    saveParams();
    engine.sendAllParams(params);
    syncAllControls();
    renderPresets();
  });
}

engine.onMeters = (m) => {
  const inDb = dbFromLinear(m.inPeak);
  const outDb = dbFromLinear(m.outPeak);
  document.getElementById('inMeter').style.width = `${pctFromDb(inDb)}%`;
  document.getElementById('outMeter').style.width = `${pctFromDb(outDb)}%`;
  document.getElementById('inDb').textContent = inDb <= -90 ? '-∞ dB' : `${inDb.toFixed(1)} dB`;
  document.getElementById('outDb').textContent = outDb <= -90 ? '-∞ dB' : `${outDb.toFixed(1)} dB`;

  const gateBadge = document.getElementById('gateBadge');
  gateBadge.textContent = m.gateGain < 0.9 ? 'Gate: Closed' : 'Gate: Open';
  gateBadge.classList.toggle('active', m.gateGain < 0.9);

  const nsBadge = document.getElementById('nsBadge');
  nsBadge.textContent = m.nsGain < 0.95 ? 'NS: Active' : 'NS: Idle';
  nsBadge.classList.toggle('active', m.nsGain < 0.95);

  const limBadge = document.getElementById('limBadge');
  limBadge.textContent = m.limiterGain < 0.98 ? 'Limiter: Clamping' : 'Limiter: Safe';
  limBadge.classList.toggle('active', m.limiterGain < 0.98);

  const howlBadge = document.getElementById('howlBadge');
  howlBadge.textContent = m.activeNotches > 0 ? `Howl Guard: ${m.activeNotches} notch` : 'Howl Guard: Ready';
  howlBadge.classList.toggle('active', m.activeNotches > 0);
};

engine.onStatus = (s) => {
  const badge = document.getElementById('latencyBadge');
  const line = document.getElementById('statusLine');
  if (s.running) {
    const totalMs = s.baseLatencyMs + s.outputLatencyMs;
    badge.textContent = totalMs > 0 ? `~${totalMs} ms` : `${s.sampleRate} Hz`;
    badge.classList.add('active');
    line.textContent = `Live @ ${s.sampleRate} Hz • C++17 WASM DSP`;
  } else {
    badge.textContent = 'Standby';
    badge.classList.remove('active');
    line.textContent = 'Offline C++17 WASM DSP • Ready';
  }
};

engine.onWasmError = (msg) => showError(`WASM DSP error: ${msg}`);

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredInstallPrompt = e;
  document.getElementById('installBtn').classList.remove('hidden');
});

document.getElementById('installBtn').addEventListener('click', async () => {
  if (!deferredInstallPrompt) return;
  deferredInstallPrompt.prompt();
  await deferredInstallPrompt.userChoice;
  deferredInstallPrompt = null;
  document.getElementById('installBtn').classList.add('hidden');
});

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('./sw.js').catch(() => {});
}

const savedTheme = localStorage.getItem(THEME_KEY) || 'orange';
document.documentElement.setAttribute('data-theme', savedTheme);
document.querySelectorAll('[data-palette]').forEach((b) =>
  b.classList.toggle('active', b.dataset.palette === savedTheme)
);

initTabs();
renderSliders();
renderPresets();
syncAllControls();
initControls();
refreshDevices().catch(() => {});

import { PARAM_IDS } from './presets.js';

export class WebAudioEngine {
  constructor() {
    this.ctx = null;
    this.stream = null;
    this.source = null;
    this.worklet = null;
    this.running = false;
    this.wasmBytes = null;
    this.recording = false;
    this.recLeft = [];
    this.recRight = [];
    this.recSamples = 0;

    this.onMeters = null;
    this.onStatus = null;
    this.onWasmError = null;
  }

  async preloadWasm() {
    if (this.wasmBytes) return this.wasmBytes;
    const res = await fetch('./dsp.wasm');
    if (!res.ok) {
      throw new Error(`dsp.wasm HTTP ${res.status} (compile with web/wasm/build.sh or deploy via GitHub Pages)`);
    }
    this.wasmBytes = await res.arrayBuffer();
    return this.wasmBytes;
  }

  async listDevices() {
    if (!navigator.mediaDevices?.enumerateDevices) return { inputs: [], outputs: [] };
    const devices = await navigator.mediaDevices.enumerateDevices();
    return {
      inputs: devices.filter((d) => d.kind === 'audioinput'),
      outputs: devices.filter((d) => d.kind === 'audiooutput'),
    };
  }

  supportsSinkSelection() {
    return typeof AudioContext !== 'undefined' && typeof AudioContext.prototype.setSinkId === 'function';
  }

  async setOutputDevice(deviceId) {
    if (!this.ctx || !this.supportsSinkSelection()) return false;
    await this.ctx.setSinkId(deviceId || '');
    return true;
  }

  async start({ inputDeviceId = '', outputDeviceId = '', params }) {
    if (this.running) return;
    const wasmBytes = await this.preloadWasm();

    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    this.ctx = new AudioCtx({ latencyHint: 'interactive', sampleRate: 48000 });

    if (outputDeviceId && this.supportsSinkSelection()) {
      await this.ctx.setSinkId(outputDeviceId).catch(() => {});
    }

    const constraints = {
      audio: {
        echoCancellation: false,
        noiseSuppression: false,
        autoGainControl: false,
        channelCount: { ideal: 2 },
        ...(inputDeviceId ? { deviceId: { exact: inputDeviceId } } : {}),
      },
      video: false,
    };

    this.stream = await navigator.mediaDevices.getUserMedia(constraints);
    await this.ctx.audioWorklet.addModule('./worklet/haramain-processor.js');

    this.source = this.ctx.createMediaStreamSource(this.stream);
    this.worklet = new AudioWorkletNode(this.ctx, 'haramain-processor', {
      numberOfInputs: 1,
      numberOfOutputs: 1,
      outputChannelCount: [2],
    });

    this.worklet.port.onmessage = (event) => {
      const msg = event.data;
      if (!msg) return;
      if (msg.type === 'meters' && this.onMeters) {
        this.onMeters(msg);
      } else if (msg.type === 'wasm-ready') {
        this.onStatus?.({
          running: true,
          sampleRate: msg.sampleRate,
          baseLatencyMs: Math.round((this.ctx.baseLatency || 0) * 1000),
          outputLatencyMs: Math.round((this.ctx.outputLatency || 0) * 1000),
        });
      } else if (msg.type === 'wasm-error') {
        this.onWasmError?.(msg.message);
      } else if (msg.type === 'record-chunk' && this.recording) {
        const l = new Float32Array(msg.left);
        const r = new Float32Array(msg.right);
        this.recLeft.push(l);
        this.recRight.push(r);
        this.recSamples += l.length;
      }
    };

    // Send a copy so preload cache remains intact across restarts
    const copy = wasmBytes.slice(0);
    this.worklet.port.postMessage({ type: 'init-wasm', wasmBytes: copy }, [copy]);
    this.sendAllParams(params);

    this.source.connect(this.worklet);
    this.worklet.connect(this.ctx.destination);
    if (this.ctx.state === 'suspended') {
      await this.ctx.resume();
    }
    this.running = true;
  }

  stop() {
    this.running = false;
    if (this.recording) this.stopRecording();
    try { this.source?.disconnect(); } catch {}
    try { this.worklet?.disconnect(); } catch {}
    if (this.stream) {
      this.stream.getTracks().forEach((t) => t.stop());
      this.stream = null;
    }
    if (this.ctx) {
      this.ctx.close().catch(() => {});
      this.ctx = null;
    }
    this.worklet = null;
    this.source = null;
    this.onStatus?.({ running: false, sampleRate: 0, baseLatencyMs: 0, outputLatencyMs: 0 });
  }

  setParam(key, value) {
    const id = PARAM_IDS[key];
    if (id === undefined || !this.worklet) return;
    this.worklet.port.postMessage({ type: 'set-param', id, value: Number(value) });
  }

  sendAllParams(params) {
    if (!this.worklet || !params) return;
    const entries = Object.entries(PARAM_IDS)
      .filter(([key]) => params[key] !== undefined)
      .map(([key, id]) => [id, Number(params[key])]);
    this.worklet.port.postMessage({ type: 'set-params-batch', entries });
  }

  startRecording(dry = false) {
    if (!this.worklet || !this.running) return;
    this.recLeft = [];
    this.recRight = [];
    this.recSamples = 0;
    this.recording = true;
    this.worklet.port.postMessage({ type: 'recording', active: true, dry });
  }

  stopRecording() {
    this.recording = false;
    this.worklet?.port.postMessage({ type: 'recording', active: false, dry: false });
    if (this.recSamples === 0) return null;

    const sampleRate = this.ctx?.sampleRate || 48000;
    const wavBlob = encodeStereoWav16(this.recLeft, this.recRight, this.recSamples, sampleRate);
    this.recLeft = [];
    this.recRight = [];
    this.recSamples = 0;
    return wavBlob;
  }
}

function encodeStereoWav16(leftChunks, rightChunks, totalSamples, sampleRate) {
  const dataSize = totalSamples * 4; // 2 channels * 2 bytes
  const buffer = new ArrayBuffer(44 + dataSize);
  const view = new DataView(buffer);

  const writeStr = (off, s) => {
    for (let i = 0; i < s.length; i++) view.setUint8(off + i, s.charCodeAt(i));
  };

  writeStr(0, 'RIFF');
  view.setUint32(4, 36 + dataSize, true);
  writeStr(8, 'WAVE');
  writeStr(12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, 2, true); // stereo
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 4, true);
  view.setUint16(32, 4, true);
  view.setUint16(34, 16, true);
  writeStr(36, 'data');
  view.setUint32(40, dataSize, true);

  let offset = 44;
  for (let c = 0; c < leftChunks.length; c++) {
    const l = leftChunks[c];
    const r = rightChunks[c];
    for (let i = 0; i < l.length; i++) {
      const sl = Math.max(-1, Math.min(1, l[i]));
      const sr = Math.max(-1, Math.min(1, r[i]));
      view.setInt16(offset, sl < 0 ? sl * 0x8000 : sl * 0x7fff, true);
      view.setInt16(offset + 2, sr < 0 ? sr * 0x8000 : sr * 0x7fff, true);
      offset += 4;
    }
  }
  return new Blob([buffer], { type: 'audio/wav' });
}

class HaramainProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.wasm = null;
    this.memory = null;
    this.inLPtr = 0;
    this.inRPtr = 0;
    this.outLPtr = 0;
    this.outRPtr = 0;
    this.recording = false;
    this.recordDry = false;
    this.meterCounter = 0;
    this.pendingParams = [];

    this.port.onmessage = async (event) => {
      const msg = event.data;
      if (!msg || !msg.type) return;

      if (msg.type === 'init-wasm') {
        try {
          const imports = {
            wasi_snapshot_preview1: {
              fd_write: () => 0,
              fd_seek: () => 0,
              fd_close: () => 0,
              proc_exit: () => {},
            },
            env: {
              emscripten_notify_memory_growth: () => {},
            },
          };
          const { instance } = await WebAssembly.instantiate(msg.wasmBytes, imports);
          this.wasm = instance.exports;
          this.memory = this.wasm.memory;
          if (typeof this.wasm._initialize === 'function') {
            this.wasm._initialize();
          }
          this.wasm.dsp_init(sampleRate);
          this.inLPtr = this.wasm.dsp_get_in_l_ptr() >>> 2;
          this.inRPtr = this.wasm.dsp_get_in_r_ptr() >>> 2;
          this.outLPtr = this.wasm.dsp_get_out_l_ptr() >>> 2;
          this.outRPtr = this.wasm.dsp_get_out_r_ptr() >>> 2;

          for (const [id, val] of this.pendingParams) {
            this.wasm.dsp_set_param(id, val);
          }
          this.pendingParams = [];
          this.port.postMessage({ type: 'wasm-ready', sampleRate });
        } catch (err) {
          this.port.postMessage({ type: 'wasm-error', message: String(err) });
        }
      } else if (msg.type === 'set-param') {
        if (this.wasm) {
          this.wasm.dsp_set_param(msg.id, msg.value);
        } else {
          this.pendingParams.push([msg.id, msg.value]);
        }
      } else if (msg.type === 'set-params-batch') {
        for (const [id, val] of msg.entries) {
          if (this.wasm) {
            this.wasm.dsp_set_param(id, val);
          } else {
            this.pendingParams.push([id, val]);
          }
        }
      } else if (msg.type === 'recording') {
        this.recording = Boolean(msg.active);
        this.recordDry = Boolean(msg.dry);
      }
    };
  }

  process(inputs, outputs) {
    const input = inputs[0];
    const output = outputs[0];
    if (!output || output.length === 0) return true;

    const outL = output[0];
    const outR = output[1] || output[0];
    const frames = outL.length;

    const inL = input && input[0] ? input[0] : null;
    const inR = input && input[1] ? input[1] : inL;

    if (!this.wasm || !this.memory) {
      outL.fill(0);
      if (outR !== outL) outR.fill(0);
      return true;
    }

    const heap = new Float32Array(this.memory.buffer);
    if (inL) {
      heap.set(inL.subarray(0, frames), this.inLPtr);
      heap.set(inR.subarray(0, frames), this.inRPtr);
    } else {
      heap.fill(0, this.inLPtr, this.inLPtr + frames);
      heap.fill(0, this.inRPtr, this.inRPtr + frames);
    }

    this.wasm.dsp_process(frames);

    const procL = heap.subarray(this.outLPtr, this.outLPtr + frames);
    const procR = heap.subarray(this.outRPtr, this.outRPtr + frames);
    outL.set(procL);
    if (outR !== outL) outR.set(procR);

    if (this.recording && inL) {
      const recL = new Float32Array(frames);
      const recR = new Float32Array(frames);
      if (this.recordDry) {
        recL.set(inL.subarray(0, frames));
        recR.set(inR.subarray(0, frames));
      } else {
        recL.set(procL);
        recR.set(procR);
      }
      this.port.postMessage(
        { type: 'record-chunk', left: recL.buffer, right: recR.buffer },
        [recL.buffer, recR.buffer]
      );
    }

    this.meterCounter += frames;
    if (this.meterCounter >= 2400) {
      this.meterCounter = 0;
      this.port.postMessage({
        type: 'meters',
        inPeak: this.wasm.dsp_get_in_peak(),
        outPeak: this.wasm.dsp_get_out_peak(),
        gateGain: this.wasm.dsp_get_gate_gain(),
        nsGain: this.wasm.dsp_get_ns_gain(),
        limiterGain: this.wasm.dsp_get_limiter_gain(),
        compGrDb: this.wasm.dsp_get_comp_gr_db(),
        activeNotches: this.wasm.dsp_get_active_notches(),
      });
    }

    return true;
  }
}

registerProcessor('haramain-processor', HaramainProcessor);

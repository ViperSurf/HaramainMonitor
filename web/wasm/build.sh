#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_WASM="$ROOT_DIR/web/dsp.wasm"

EXPORTS='["_dsp_init","_dsp_get_in_l_ptr","_dsp_get_in_r_ptr","_dsp_get_out_l_ptr","_dsp_get_out_r_ptr","_dsp_process","_dsp_set_param","_dsp_get_in_peak","_dsp_get_out_peak","_dsp_get_gate_gain","_dsp_get_ns_gain","_dsp_get_limiter_gain","_dsp_get_comp_gr_db","_dsp_get_active_notches"]'

em++ -O3 -std=c++17 -D_USE_MATH_DEFINES --no-entry \
  -s STANDALONE_WASM=1 \
  -s INITIAL_MEMORY=4194304 \
  -s ALLOW_MEMORY_GROWTH=0 \
  -s "EXPORTED_FUNCTIONS=$EXPORTS" \
  -I "$ROOT_DIR/app/src/main/cpp/dsp" \
  "$SCRIPT_DIR/dsp_wasm.cpp" \
  -o "$OUT_WASM"

echo "Built $OUT_WASM ($(wc -c < "$OUT_WASM") bytes)"

#pragma once
#include <cmath>
#include <cstddef>
#include <vector>
#include <algorithm>
#include <complex>

// Worker-thread feedback (howl) detector. A howl is a narrowband tone
// that dominates the spectrum and SUSTAINS in one place. Voice fails
// the test on purpose: harmonics spread energy across many bins, and
// real pitch never parks inside one FFT bin for ~400 ms.
//
// Method: 4096-point Hann-windowed FFT (its own frequency grid — a
// tone always lands within half a bin of a grid point, so there are
// no scalloping nulls). The peak bin in 300 Hz..6 kHz "rings" when:
//   - its band (peak +- 2 bins) is loud (>= ~-18 dBFS),
//   - the band holds >= 55% of the energy in peak +- 48 bins
//     (narrowband concentration),
//   - the band is >= 6x the median band level (amplitude),
//   - and no strong component sits near f/2 or f/3 — that would make
//     the peak one harmonic of a voice-like stack, not a lone tone.
// The SAME bin (within 2) must ring on 5 consecutive analyses
// (~80 ms apart) before a frequency is reported.
class HowlDetector {
public:
    void prepare(double sampleRate) {
        sr_ = sampleRate;
        streakBin_ = -1;
        streak_ = 0;
    }

    // x: mono samples (uses the LAST 4096). Returns Hz or 0.
    float analyze(const float *x, size_t n) {
        if (n < 1024) return 0.0f;
        const size_t N = 4096;
        const size_t avail = std::min(n, N);
        re_.assign(N, 0.0f);
        im_.assign(N, 0.0f);
        const size_t off = n - avail;
        for (size_t i = 0; i < avail; ++i) {
            const double hann = 0.5 - 0.5 * std::cos(2.0 * M_PI * double(i) / double(N - 1));
            re_[i] = static_cast<float>(x[off + i] * hann);
        }
        fft(re_, im_);
        auto power = [&](int k) -> double {
            if (k < 0 || k >= static_cast<int>(N / 2)) return 0.0;
            return double(re_[k]) * re_[k] + double(im_[k]) * im_[k];
        };
        auto bandPower = [&](int center, int radius) {
            double s = 0.0;
            for (int k = center - radius; k <= center + radius; ++k) s += power(k);
            return s;
        };
        const int kLo = static_cast<int>(300.0 * N / sr_);
        const int kHi = static_cast<int>(6000.0 * N / sr_);
        int peak = kLo;
        for (int k = kLo; k <= kHi; ++k) if (power(k) > power(peak)) peak = k;
        const double peakBand = bandPower(peak, 2);
        const double amp = 2.0 * std::sqrt(peakBand) / (0.5 * double(N));
        // Median 5-bin band level across the range.
        std::vector<double> bands;
        for (int k = kLo + 2; k <= kHi - 2; k += 5) bands.push_back(std::sqrt(bandPower(k, 2)));
        std::nth_element(bands.begin(), bands.begin() + bands.size() / 2, bands.end());
        const double medianBand = bands.empty() ? 0.0 : bands[bands.size() / 2];
        const double wide = bandPower(peak, 48);
        auto ampAt = [&](double f) {
            const int k = static_cast<int>(f * N / sr_ + 0.5);
            return 2.0 * std::sqrt(bandPower(k, 2)) / (0.5 * double(N));
        };
        const double peakHz = peak * sr_ / double(N);
        const bool harmonicStack = ampAt(peakHz / 2.0) > 0.35 * amp
            || ampAt(peakHz / 3.0) > 0.35 * amp;
        const bool ringing = amp >= 0.12
            && peakBand >= 0.55 * wide
            && std::sqrt(peakBand) >= 6.0 * medianBand
            && !harmonicStack;
        if (!ringing) { streakBin_ = -1; streak_ = 0; return 0.0f; }
        if (streakBin_ >= 0 && std::abs(peak - streakBin_) <= 2) ++streak_;
        else { streakBin_ = peak; streak_ = 1; }
        return streak_ >= 5 ? static_cast<float>(peakHz) : 0.0f;
    }

private:
    // Iterative radix-2 FFT (size must be a power of two).
    static void fft(std::vector<float> &re, std::vector<float> &im) {
        const size_t n = re.size();
        for (size_t i = 1, j = 0; i < n; ++i) {
            size_t bit = n >> 1;
            for (; j & bit; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { std::swap(re[i], re[j]); std::swap(im[i], im[j]); }
        }
        for (size_t len = 2; len <= n; len <<= 1) {
            const double ang = -2.0 * M_PI / double(len);
            const auto wl = std::polar(1.0, ang);
            for (size_t i = 0; i < n; i += len) {
                std::complex<double> w(1.0, 0.0);
                for (size_t k = 0; k < len / 2; ++k) {
                    const std::complex<double> u(re[i + k], im[i + k]);
                    const std::complex<double> v(re[i + k + len / 2] * w.real() - im[i + k + len / 2] * w.imag(),
                                                 re[i + k + len / 2] * w.imag() + im[i + k + len / 2] * w.real());
                    re[i + k] = static_cast<float>((u + v).real());
                    im[i + k] = static_cast<float>((u + v).imag());
                    re[i + k + len / 2] = static_cast<float>((u - v).real());
                    im[i + k + len / 2] = static_cast<float>((u - v).imag());
                    w *= wl;
                }
            }
        }
    }

    double sr_ = 48000;
    std::vector<float> re_, im_;
    int streakBin_ = -1;
    int streak_ = 0;
};

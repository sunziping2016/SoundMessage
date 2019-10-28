package io.szp.soundmessage;

import java.util.Deque;

public class SignalProcessing {
    public static final int[] FACTORS = new int[] { 2, 3, 5, 7 };

    public static float[] chirp(float f0, float f1, float[] t) {
        return chirp(f0, f1, t, 0);
    }

    public static float[] chirp(float f0, float f1, float[] t, float angle) {
        float beta = (f1 - f0) / t[t.length - 1];
        float[] value = new float[t.length];
        for (int i = 0; i < t.length; ++i) {
            value[i] = (float) Math.cos(2 * Math.PI *
                    (beta / 2 * (t[i] * t[i]) + f0 * t[i]) + angle);
        }
        return value;
    }

    public static void xcorr(float[] x, float[] y, Box<float[]> cor, Box<int []> lags) {
        int m = Math.max(x.length, y.length), mxl = m - 1, m2 = 2 * m;
        while (true) {
            int r = m2;
            for (int p: FACTORS) {
                while (r > 1 && r % p == 0)
                    r /= p;
            }
            if (r == 1)
                break;
            ++m2;
        }
        float[] xReal = new float[m2], yReal = new float[m2];
        float[] xImag = new float[m2], yImag = new float[m2];
        System.arraycopy(x, 0, xReal, 0, x.length);
        System.arraycopy(y, 0, yReal, 0, y.length);
        FFT.fft(xReal, xImag);
        FFT.fft(yReal, yImag);
        for (int i = 0; i < m2; ++i) {
            float temp = xImag[i] * yReal[i] - xReal[i] * yImag[i];
            xReal[i] = xReal[i] * yReal[i] + xImag[i] * yImag[i];
            xImag[i] = temp;
        }
        FFT.ifft(xReal, xImag);
        int length = 2 * mxl + 1;
        cor.value = new float[length];
        lags.value = new int[length];
        System.arraycopy(xReal, m2 - mxl, cor.value, 0, mxl);
        System.arraycopy(xReal, 0, cor.value, mxl, mxl + 1);
        int begin = -mxl;
        for (int i = 0; i < length; ++i)
            lags.value[i] = begin + i;
    }

    public static void max(float[] input, Box<Float> max, Box<Integer> index) {
        float floatMax = Float.NEGATIVE_INFINITY;
        int intIndex = 0;
        for (int i = 0; i < input.length; ++i) {
            if (input[i] > floatMax) {
                floatMax = input[i];
                intIndex = i;
            }
        }
        max.value = floatMax;
        index.value = intIndex;
    }

    public static float mean(Deque<Integer> input) {
        float sum = 0;
        for (Integer i: input)
            sum += i;
        return sum / input.size();
    }

    public static float mean(float[] input) {
        float sum = 0;
        for (float i: input)
            sum += i;
        return sum / input.length;
    }

    public static float meanAbs(float[] input) {
        float sum = 0;
        for (float i: input)
            sum += Math.abs(i);
        return sum / input.length;
    }

    public static float stdev(Deque<Integer> input, float mean) {
        float sum = 0;
        for (Integer i: input) {
            float diff = i - mean;
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum / input.size());
    }

    private static final float[] QPSK_PHASES = new float[] {
            (float) (1 * Math.PI / 4),
            (float) (7 * Math.PI / 4),
            (float) (3 * Math.PI / 4),
            (float) (5 * Math.PI / 4)
    };
    private static final int[] QPSK_INDEX = new int[] { 3, 1, 0, 2 };

    public interface PSK {
        void modulate(int[] input, Box<float[]> real, Box<float[]> imag);
        int[] demodulate(float[] real, float[] imag);
    }

    public static final PSK bpsk = new PSK() {
        @Override
        public void modulate(int[] input, Box<float[]> real, Box<float[]> imag) {
            // TODO
        }

        @Override
        public int[] demodulate(float[] real, float[] imag) {
            // TODO
            return new int[0];
        }
    };

    public static final PSK qpsk = new PSK() {
        @Override
        public void modulate(int[] input, Box<float[]> real, Box<float[]> imag) {
            real.value = new float[input.length];
            imag.value = new float[input.length];
            for (int i = 0; i < input.length; ++i) {
                float phase = QPSK_PHASES[input[i]];
                real.value[i] = (float) Math.cos(phase);
                imag.value[i] = (float) Math.sin(phase);
            }
        }

        @Override
        public int[] demodulate(float[] real, float[] imag) {
            int[] result = new int[real.length];
            for (int i = 0; i < real.length; ++i) {
                int index = (int) Math.round((Math.atan2(imag[i], real[i]) +
                        Math.PI - Math.PI/4) / (Math.PI/2));
                result[i] = QPSK_INDEX[index];
            }
            return result;
        }
    };

    public static final PSK psk8 = new PSK() {
        @Override
        public void modulate(int[] input, Box<float[]> real, Box<float[]> imag) {
            // TODO
        }

        @Override
        public int[] demodulate(float[] real, float[] imag) {
            // TODO
            return new int[0];
        }
    };

    public static PSK getPSK(int num) {
        switch (num) {
            case 2:
                return bpsk;
            case 4:
                return qpsk;
            case 8:
                return psk8;
            default:
                throw new AssertionError("Unknown PSK method");
        }
    }

    public static void main(String[] args) {
        float[] x = new float[] {1,2,3,4,5};
        float[] y = new float[] {2,1,3};
        Box<float[]> cor = new Box<>();
        Box<int[]> lags = new Box<>();
        xcorr(x, y, cor, lags);
        for (int i = 0; i < cor.value.length; ++i)
            System.out.println(cor.value[i]);
    }
}
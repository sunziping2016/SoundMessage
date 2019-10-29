package io.szp.soundmessage;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
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

    public static int[] encodeText(String text, int n, int subcarrierNum) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int byteNum = textBytes.length;
        int encodedNum = (int) Math.round(Math.ceil((double) (byteNum + 8) * 8 / n));
        int symbolNum = (int) Math.round(Math.ceil((double) encodedNum / subcarrierNum));
        byte[] data = new byte[byteNum + 8];
        System.arraycopy(textBytes, 0, data, 8, byteNum);
        data[0] = (byte) (symbolNum & 0xff);
        data[1] = (byte) ((symbolNum >> 8) & 0xff);
        data[2] = (byte) ((symbolNum >> 16) & 0xff);
        data[3] = (byte) ((symbolNum >> 24) & 0xff);
        data[4] = (byte) (byteNum & 0xff);
        data[5] = (byte) ((byteNum >> 8) & 0xff);
        data[6] = (byte) ((byteNum >> 16) & 0xff);
        data[7] = (byte) ((byteNum >> 24) & 0xff);
        BitSet bits = BitSet.valueOf(data);
        int[] result = new int[symbolNum * subcarrierNum];
        for (int i = 0; i < symbolNum; ++i) {
            for (int j = 0; j < subcarrierNum; ++j) {
                int index = i * subcarrierNum + j;
                long[] num = bits.get(index * n, (index + 1) * n).toLongArray();
                if (num.length > 0)
                    result[index] = (int) num[0];
            }
        }
        return result;
    }

    public static String decodeText(int[] data, int n, int subcarrierNum) {
        int symbolNum = data.length / subcarrierNum;
        if (data.length != symbolNum * subcarrierNum)
            return null;
        BitSet bits = new BitSet();
        for (int i = 0; i < data.length; ++i) {
            int num = data[i];
            for (int j = 0; j < n; ++j)
                bits.set(n * i + j, ((num >> j) & 1) == 1);
        }
        byte[] bytes = bits.toByteArray();
        if (bytes.length < 8)
            return null;
        int receivedSymbolNum = bytes[0] + ((int) bytes[1] << 8) +
                ((int) bytes[2] << 16) + ((int) bytes[3] << 24);
        if (receivedSymbolNum != symbolNum)
            return null;
        int byteNum = bytes[4] + ((int) bytes[5] << 8) +
                ((int) bytes[6] << 16) + ((int) bytes[7] << 24);
        byte[] textBytes = new byte[byteNum];
        if (bytes.length - 8 >= byteNum)
            System.arraycopy(bytes, 8, textBytes, 0, byteNum);
        else
            System.arraycopy(bytes, 8, textBytes, 0, bytes.length - 8);
        return new String(textBytes, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        int[] result = encodeText("你好", 3, 1);
        String text = decodeText(result, 3, 1);
        if (text == null) {
            System.out.println("Wrong decode");
            return;
        }
        System.out.println(text);
//        for (int value : result)
//            System.out.println(value);
    }
}
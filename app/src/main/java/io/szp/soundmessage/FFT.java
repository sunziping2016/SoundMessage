package io.szp.soundmessage;

public class FFT {
    public static void fft(float[] real, float[] imag) {
        if (real.length != imag.length)
            throw new IllegalArgumentException("Mismatched lengths");
        int n = real.length;
        if (n == 0)
            return;
        if ((n & (n - 1)) == 0) // Is power of 2
            fftRadix2(real, imag);
        else
            // More complicated algorithm for arbitrary sizes
            fftBluestein(real, imag);
    }

    public static void fftRadix2(float[] real, float[] imag) {
        // Initialization
        if (real.length != imag.length)
            throw new IllegalArgumentException("Mismatched lengths");
        int n = real.length;
        if (n <= 1)
            return;
        int levels = -1;
        for (int i = 0; i < 32; i++) {
            if (1 << i == n)
                levels = i; // Equal to log2(n)
        }
        if (levels == -1)
            throw new IllegalArgumentException("Length is not a power of 2");
        float[] cosTable = new float[n / 2];
        float[] sinTable = new float[n / 2];
        cosTable[0] = 1;
        sinTable[0] = 0;
        float qc = (float) Math.cos(2 * Math.PI / n), qs = (float) Math.sin(2 * Math.PI / n);
        for (int i = 1; i < n / 2; i++) {
            cosTable[i] = cosTable[i - 1] * qc - sinTable[i - 1] * qs;
            sinTable[i] = sinTable[i - 1] * qc + cosTable[i - 1] * qs;
        }

        // Bit-reversed addressing permutation
        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (32 - levels);
            if (j > i) {
                float temp = real[i];
                real[i] = real[j];
                real[j] = temp;
                temp = imag[i];
                imag[i] = imag[j];
                imag[j] = temp;
            }
        }

        // Cooley-Tukey decimation-in-time radix-2 FFT
        for (int size = 2; size <= n; size *= 2) {
            int halfsize = size / 2;
            int tablestep = n / size;
            for (int i = 0; i < n; i += size) {
                for (int j = i, k = 0; j < i + halfsize; j++, k += tablestep) {
                    float tpre = real[j + halfsize] * cosTable[k] + imag[j + halfsize] * sinTable[k];
                    float tpim = -real[j + halfsize] * sinTable[k] + imag[j + halfsize] * cosTable[k];
                    real[j + halfsize] = real[j] - tpre;
                    imag[j + halfsize] = imag[j] - tpim;
                    real[j] += tpre;
                    imag[j] += tpim;
                }
            }
        }
    }

    public static void fftBluestein(float[] real, float[] imag) {
        // Find a power-of-2 convolution length m such that m >= n * 2 - 1
        int n = real.length;
        int m = 1;
        while (m < n * 2 - 1)
            m *= 2;

        // Trignometric tables
        float[] tc = new float[2 * n];
        float[] ts = new float[2 * n];
        tc[0] = 1;
        ts[0] = 0;
        float qc = (float) Math.cos(Math.PI / n), qs = (float) Math.sin(Math.PI / n);
        for (int i = 1; i < 2 * n; i++) {
            tc[i] = tc[i - 1] * qc - ts[i - 1] * qs;
            ts[i] = ts[i - 1] * qc + tc[i - 1] * qs;
        }
        float[] cosTable = new float[n];
        float[] sinTable = new float[n];
        for (int i = 0; i < n; i++) {
            int j = (int) ((long) i * i % (n * 2)); // This is more accurate than j = i * i
            cosTable[i] = tc[j];
            sinTable[i] = ts[j];
        }

        // Temporary vectors and preprocessing
        float[] aReal = new float[m];
        float[] aImag = new float[m];
        for (int i = 0; i < n; i++) {
            aReal[i] = real[i] * cosTable[i] + imag[i] * sinTable[i];
            aImag[i] = -real[i] * sinTable[i] + imag[i] * cosTable[i];
        }
        float[] bReal = new float[m];
        float[] bImag = new float[m];
        bReal[0] = cosTable[0];
        bImag[0] = sinTable[0];
        for (int i = 1; i < n; i++) {
            bReal[i] = bReal[m - i] = cosTable[i];
            bImag[i] = bImag[m - i] = sinTable[i];
        }

        // Convolution
        float[] cReal = new float[m];
        float[] cImag = new float[m];

        cconv(aReal, aImag, bReal, bImag, cReal, cImag);

        // Postprocessing
        for (int i = 0; i < n; i++) {
            real[i] = cReal[i] * cosTable[i] + cImag[i] * sinTable[i];
            imag[i] = -cReal[i] * sinTable[i] + cImag[i] * cosTable[i];
        }
    }

    public static void ifft(float[] real, float[] imag) {
        fft(imag, real);
        int n = real.length;
        for (int i = 0; i < n; i++) { // Scaling (because this FFT implementation omits it)
            real[i] = real[i] / n;
            imag[i] = imag[i] / n;
        }
    }

    public static void cconv(float[] xReal, float[] xImag, float[] yReal, float[] yImag,
                             float[] outReal, float[] outImag) {
        if (xReal.length != xImag.length || xReal.length != yReal.length
                || yReal.length != yImag.length || xReal.length != outReal.length
                || outReal.length != outImag.length)
            throw new IllegalArgumentException("Mismatched lengths");

        int n = xReal.length;

        fft(xReal, xImag);
        fft(yReal, yImag);
        for (int i = 0; i < n; i++) {
            outReal[i] = xReal[i] * yReal[i] - xImag[i] * yImag[i];
            outImag[i] = xImag[i] * yReal[i] + xReal[i] * yImag[i];
        }
        ifft(outReal, outImag);
    }
}


package org.hamming;

import java.nio.ByteBuffer;

public class HammingCodec {

    // N: 8, 1024, 16384
    public static byte[] protect(byte[] original, int N) {
        int m = (int) (Math.log(N) / Math.log(2));
        int K = N - m - 1; // bits de datos por cada bloque

        boolean[] dataBits = BitManipulator.unpackBits(original, original.length * 8);
        
        // Agregar 32 bits (4 bytes) al principio para guardar el tamaño original en bytes
        int L_bytes = original.length;
        boolean[] headerBits = BitManipulator.unpackBits(ByteBuffer.allocate(4).putInt(L_bytes).array(), 32);

        boolean[] allData = new boolean[32 + dataBits.length];
        System.arraycopy(headerBits, 0, allData, 0, 32);
        System.arraycopy(dataBits, 0, allData, 32, dataBits.length);

        int numBlocks = (allData.length + K - 1) / K;
        boolean[] encodedStream = new boolean[numBlocks * N];

        int dataIndex = 0;
        for (int b = 0; b < numBlocks; b++) {
            int blockOffset = b * N;
            
            // 1. Llenar los bits de datos
            for (int i = 1; i < N; i++) {
                if ((i & (i - 1)) != 0) { // No es potencia de 2 (es bit de dato)
                    if (dataIndex < allData.length) {
                        encodedStream[blockOffset + i] = allData[dataIndex++];
                    } else {
                        encodedStream[blockOffset + i] = false; // Padding
                    }
                }
            }

            // 2. Calcular los bits de paridad (1, 2, 4, 8...)
            for (int j = 0; j < m; j++) {
                int p = 1 << j;
                boolean parityVal = false;
                for (int i = 1; i < N; i++) {
                    if ((i & p) != 0 && i != p) {
                        parityVal ^= encodedStream[blockOffset + i];
                    }
                }
                encodedStream[blockOffset + p] = parityVal;
            }

            // 3. Paridad global (bit 0)
            boolean globalParity = false;
            for (int i = 1; i < N; i++) {
                globalParity ^= encodedStream[blockOffset + i];
            }
            encodedStream[blockOffset + 0] = globalParity;
        }

        return BitManipulator.packBits(encodedStream);
    }

    public static byte[] introduceErrors(byte[] protectedFile, int N) {
        boolean[] stream = BitManipulator.unpackBits(protectedFile, protectedFile.length * 8);
        int numBlocks = stream.length / N;

        for (int b = 0; b < numBlocks; b++) {
            // 50% de probabilidad de que haya error en el bloque
            if (Math.random() < 0.5) {
                int errorPos = (int) (Math.random() * N); // entre 0 y N-1
                stream[b * N + errorPos] ^= true; // invertir el bit
            }
        }
        return BitManipulator.packBits(stream);
    }

    public static byte[] unprotect(byte[] protectedFile, int N, boolean correctErrors) {
        int m = (int) (Math.log(N) / Math.log(2));
        int K = N - m - 1; 

        boolean[] stream = BitManipulator.unpackBits(protectedFile, protectedFile.length * 8);
        int numBlocks = stream.length / N;

        boolean[] extractedData = new boolean[numBlocks * K];
        int extIndex = 0;

        for (int b = 0; b < numBlocks; b++) {
            int blockOffset = b * N;

            if (correctErrors) {
                int syndrome = 0;
                for (int j = 0; j < m; j++) {
                    int p = 1 << j;
                    boolean sum = false;
                    for (int i = 1; i < N; i++) {
                        if ((i & p) != 0) {
                            sum ^= stream[blockOffset + i];
                        }
                    }
                    if (sum) syndrome += p;
                }
                
                boolean globalSum = false;
                for (int i = 0; i < N; i++) {
                    globalSum ^= stream[blockOffset + i];
                }

                if (syndrome != 0) {
                    if (globalSum) {
                        // Error simple: corregible
                        if (syndrome < N) {
                            stream[blockOffset + syndrome] ^= true;
                        }
                    } else {
                        // Error doble: no se puede corregir de forma segura, pero como
                        // el requerimiento es un error max por modulo, esto en teoría 
                        // no deberia ocurrir si solo introducimos max 1 error.
                        if (syndrome < N) {
                             stream[blockOffset + syndrome] ^= true;
                        }
                    }
                } else if (globalSum) { // syndrome == 0 pero paridad global mal -> error en bit 0
                    stream[blockOffset + 0] ^= true;
                }
            }

            // Extraer bits de datos
            for (int i = 1; i < N; i++) {
                if ((i & (i - 1)) != 0) {
                    extractedData[extIndex++] = stream[blockOffset + i];
                }
            }
        }

        // Recuperar header de 32 bits
        boolean[] headerBits = new boolean[32];
        System.arraycopy(extractedData, 0, headerBits, 0, 32);
        byte[] headerBytes = BitManipulator.packBits(headerBits);
        int L_bytes = ByteBuffer.wrap(headerBytes).getInt();

        if (L_bytes <= 0 || L_bytes > extractedData.length / 8) {
            // Tamaño irracional (posible error sin corrección corrompiendo el header).
            // Rescatamos todo lo disponible menos el header.
            L_bytes = (extractedData.length - 32) / 8; 
        }

        boolean[] finalDataBits = new boolean[L_bytes * 8];
        System.arraycopy(extractedData, 32, finalDataBits, 0, finalDataBits.length);

        return BitManipulator.packBits(finalDataBits);
    }
}

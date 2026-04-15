package org.hamming;

public class BitManipulator {
    
    /**
     * Convierte un arreglo de bytes a un arreglo de bits empaquetado eficientemente
     * usando bits dentro de bytes.
     */
    public static byte[] packBits(boolean[] bits) {
        int byteLen = (bits.length + 7) / 8;
        byte[] bytes = new byte[byteLen];
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) {
                bytes[i / 8] |= (1 << (7 - (i % 8)));
            }
        }
        return bytes;
    }

    /**
     * Desempaqueta un arreglo de bytes en un boolean[]
     */
    public static boolean[] unpackBits(byte[] bytes, int expectedBits) {
        boolean[] bits = new boolean[expectedBits];
        for (int i = 0; i < expectedBits; i++) {
            int bitVal = bytes[i / 8] & (1 << (7 - (i % 8)));
            bits[i] = (bitVal != 0);
        }
        return bits;
    }

    /**
     * Aplica la máscara XOR entre un arreglo de bits y una semilla.
     */
    public static void applyXOR(boolean[] data, boolean[] key) {
        for (int i = 0; i < data.length; i++) {
            data[i] ^= key[i % key.length];
        }
    }
}

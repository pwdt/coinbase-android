package com.coinbase.android.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


public class CoinBaseCrypto {

    private static boolean injected;

    public static synchronized void getGWTProvider() {

        if (!injected) {
            Security.insertProviderAt(CryptoGwtProvider.INSTANCE, 0);
            injected = true;
        }
    }

    /**
     * Returns the SecretKeyFactory.
     * First check if PBKDF2WithHmacSHA1 is supported by the default crypto provider.
     * Else use the GWTProvider.
     */
    public static SecretKeyFactory getKeyFactory() {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            getGWTProvider();
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            } catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex.getMessage());
            }
        }
    }

    public static byte[] getKey(char[] password, byte[] salt, int numOfIterations, int keyLength) {
        try {
            SecretKeyFactory skf = getKeyFactory();
            KeySpec ks = new PBEKeySpec(password, salt, numOfIterations, keyLength);
            SecretKey s = skf.generateSecret(ks);
            return s.getEncoded();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
            return null;
        }
    }

}

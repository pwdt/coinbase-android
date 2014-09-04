package com.coinbase.android.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.Collections;
import java.security.Security;
import javax.crypto.SecretKeyFactory;
import javax.crypto.SecretKeyFactorySpi;
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.MacSpi;
import java.security.MessageDigestSpi;
/**
 *  Source obtained from crypto-gwt. Apache 2 License.
 *  https://code.google.com/p/crypto-gwt
 */

public class CryptoGwtProvider extends Provider{

    public static final Provider INSTANCE = new CryptoGwtProvider();


    private CryptoGwtProvider()  {

        super("CRYPTOGWT", 1.0, "");

        putService(new SpiFactoryService(
                this,
                "SecretKeyFactory",
                "PBKDF2WithHmacSHA1",
                Pbkdf2.HmacSHA1.class.getName(),
                Collections.<String>emptyList(),
                Collections.<String, String>emptyMap(),
                new SpiFactory<SecretKeyFactorySpi>() {
                    public SecretKeyFactorySpi create(Object constructorParam) throws NoSuchAlgorithmException {
                        return new Pbkdf2.HmacSHA1();
                    }
                }));
    }






}

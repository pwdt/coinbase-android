package com.coinbase.android.crypto;

import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.PBEKeySpec;
/**
 *
 *  Source obtained from crypto-gwt. Apache 2 License.
 *  https://code.google.com/p/crypto-gwt/source/browse/crypto-gwt/src/main/java/com/googlecode/
 *  cryptogwt/provider/PBESecretKeyImpl.java
 *
 */
public class PBESecretKeyImpl implements PBEKey {

    private static final long serialVersionUID = -7980717779970757743L;

    private PBEKeySpec spec;

    private String algorithm;

    private byte[] key;

    public PBESecretKeyImpl(String algorithm, PBEKeySpec spec, byte[] key) {
        this.algorithm = algorithm;
        this.spec = spec;
        this.key = key;
    }

    public int getIterationCount() {
        return spec.getIterationCount();
    }

    public char[] getPassword() {
        return spec.getPassword();
    }

    public byte[] getSalt() {
        return spec.getSalt();
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public byte[] getEncoded() {
        return key;
    }

    public String getFormat() {
        return "RAW";
    }
}
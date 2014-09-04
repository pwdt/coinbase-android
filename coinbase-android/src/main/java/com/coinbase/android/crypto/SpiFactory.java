package com.coinbase.android.crypto;

import java.security.NoSuchAlgorithmException;

/**
 *  Source obtained from crypto-gwt. Apache 2 License.
 *  https://code.google.com/p/crypto-gwt/source/browse/crypto-gwt/src/main/java/com/googlecode/
 *  cryptogwt/util/SpiFactory.java
 */



public interface SpiFactory<T> {
    T create(Object constructorParam) throws NoSuchAlgorithmException;
}

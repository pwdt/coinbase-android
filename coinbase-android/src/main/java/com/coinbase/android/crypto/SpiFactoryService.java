package com.coinbase.android.crypto;

import java.security.*;
import java.security.Provider;
import java.security.Provider.Service;
import java.util.List;
import java.util.Map;

/**
 *  Source obtained from crypto-gwt. Apache 2 License.
 *  https://code.google.com/p/crypto-gwt/source/browse/crypto-gwt/src/main/java/com/googlecode/
 *  cryptogwt/util/SpiFactoryService.java
 */

public class SpiFactoryService extends Service {

    private SpiFactory<?> factory;

    public SpiFactoryService(Provider provider, String type, String algorithm,
                             String className, List<String> aliases, Map<String, String> attributes,
                             SpiFactory<?> factory) {
        super(provider, type, algorithm, className, aliases, attributes);
        this.factory = factory;
    }

    @Override
    public Object newInstance(Object constructorParameter)
            throws NoSuchAlgorithmException {
        return factory.create(constructorParameter);
    }
}
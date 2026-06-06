package net.ldoin.shinnetai.util;

import javax.net.ssl.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;

public class SSLUtil {

    public static SSLContext createSSLContext(Path keystore, char[] keystorePassword, char[] keyPassword) throws Exception {
        char[] keystorePasswordCopy = keystorePassword != null ? keystorePassword.clone() : null;
        char[] keyPasswordCopy = keyPassword != null ? keyPassword.clone() : null;
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try {
            try (var fis = Files.newInputStream(keystore)) {
                keyStore.load(fis, keystorePasswordCopy);
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPasswordCopy != null ? keyPasswordCopy : keystorePasswordCopy);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } finally {
            if (keystorePasswordCopy != null) {
                Arrays.fill(keystorePasswordCopy, '\0');
            }
            
            if (keyPasswordCopy != null) {
                Arrays.fill(keyPasswordCopy, '\0');
            }
        }
    }

    public static SSLServerSocketFactory createServerSocketFactory(Path keystore, char[] keystorePassword, char[] keyPassword) throws Exception {
        return createSSLContext(keystore, keystorePassword, keyPassword).getServerSocketFactory();
    }

    public static SSLSocketFactory createSocketFactory(Path keystore, char[] keystorePassword, char[] keyPassword) throws Exception {
        return createSSLContext(keystore, keystorePassword, keyPassword).getSocketFactory();
    }

    public static SSLServerSocketFactory getDefaultServerSocketFactory() {
        return (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
    }

    public static SSLSocketFactory getDefaultSocketFactory() {
        return (SSLSocketFactory) SSLSocketFactory.getDefault();
    }
}
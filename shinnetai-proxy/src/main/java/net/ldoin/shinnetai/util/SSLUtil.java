package net.ldoin.shinnetai.util;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

public class SSLUtil {
    
    public static SSLContext createSSLContext(File keystore, String keystorePassword, String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystore)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }
        
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyPassword.toCharArray());
        
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
    
    public static SSLServerSocketFactory createServerSocketFactory(File keystore, String keystorePassword, String keyPassword) throws Exception {
        return createSSLContext(keystore, keystorePassword, keyPassword).getServerSocketFactory();
    }
    
    public static SSLSocketFactory createSocketFactory(File keystore, String keystorePassword, String keyPassword) throws Exception {
        return createSSLContext(keystore, keystorePassword, keyPassword).getSocketFactory();
    }
    
    public static SSLServerSocketFactory getDefaultServerSocketFactory() {
        return (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
    }
    
    public static SSLSocketFactory getDefaultSocketFactory() {
        return (SSLSocketFactory) SSLSocketFactory.getDefault();
    }
}
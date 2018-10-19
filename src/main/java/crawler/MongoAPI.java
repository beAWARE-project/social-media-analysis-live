/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawler;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.ServerAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 *
 * @author andreadisst
 */
public class MongoAPI {
    
    static String MONGO_URI = System.getenv("SECRET_MONGO_URI");
    
    public static MongoClient connect() throws UnknownHostException, NoSuchAlgorithmException, KeyManagementException{
        
        MongoClientURI mongoClientURI = new MongoClientURI(MONGO_URI);
        
        TrustManager[] trustManagers = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String t) { }
                public void checkServerTrusted(X509Certificate[] certs, String t) { }
            }
        };

        SSLContext sslContext=SSLContext.getInstance("TLS");
        sslContext.init(null,trustManagers,new SecureRandom());
        
        MongoClientOptions options = MongoClientOptions.builder().
                        sslEnabled(true).
                        sslInvalidHostNameAllowed(true).
                        socketFactory(sslContext.getSocketFactory()).
                        build();

        MongoClient mongoClient = new MongoClient(Arrays.asList(
                                        new ServerAddress(mongoClientURI.getHosts().get(0)),
                                        new ServerAddress(mongoClientURI.getHosts().get(1))), Arrays.asList(mongoClientURI.getCredentials()), options);
        
        return mongoClient;
    }
    
}

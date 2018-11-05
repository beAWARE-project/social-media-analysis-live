/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawler;

import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;

/**
 *
 * @author andreadisst
 */
public class Cryptonite {
    
    /*public static void main(String[] args) {
        String data = "";
        final String enc = getEncrypted(data);
        System.out.println("Encrypted : " + enc);
        System.out.println("Decrypted : " + getDecrypted(enc));
    }*/

    private static final String ALGORITHM = "AES";

    private static final byte[] SALT = Configuration.CRYPT_KEY.getBytes();

    public static String getEncrypted(String plainText) {

        if (plainText == null) {
            return null;
        }

        Key salt = getSalt();

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, salt);
            byte[] encodedValue = cipher.doFinal(plainText.getBytes());
            return new String(Base64.getEncoder().encode(encodedValue));
        } catch (Exception e) {
            e.printStackTrace();
        }

        throw new IllegalArgumentException("Failed to encrypt data");
    }

    public static String getDecrypted(String encodedText) {

        if (encodedText == null) {
            return null;
        }

        Key salt = getSalt();
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, salt);
            byte[] decodedValue = Base64.getDecoder().decode(encodedText);
            byte[] decValue = cipher.doFinal(decodedValue);
            return new String(decValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static Key getSalt() {
        return new SecretKeySpec(SALT, ALGORITHM);
    }
    
}

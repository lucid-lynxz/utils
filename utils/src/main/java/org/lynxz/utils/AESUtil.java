package org.lynxz.utils;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.security.Provider;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by lynxz on 2018/6
 */
public class AESUtil {
    private final static String HEX = "0123456789ABCDEF";
    private static final String CBC_PKCS5_PADDING = "AES/CBC/PKCS5Padding";//AES是加密方式 CBC是工作模式 PKCS5Padding是填充模式
    private static final String AES = "AES";//AES 加密
    private static final String SHA1PRNG = "SHA1PRNG";//// SHA1PRNG 强随机种子算法, 要区别4.2以上版本的调用方法


    // 生成随机密钥
    @SuppressLint("DeletedProvider")
    private static byte[] getRawKey(byte[] seed) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance(AES);
        //for android
        SecureRandom sr = null;
        // 在4.2以上版本中，SecureRandom获取方式发生了改变
        int sdk_version = android.os.Build.VERSION.SDK_INT;
        if (sdk_version > 23) {  // Android  6.0 以上
            sr = SecureRandom.getInstance(SHA1PRNG, new CryptoProvider());
        } else if (sdk_version >= 17) {
            sr = SecureRandom.getInstance(SHA1PRNG, "Crypto");
        } else {
            sr = SecureRandom.getInstance(SHA1PRNG);
        }
        // for Java
        // secureRandom = SecureRandom.getInstance(SHA1PRNG);
        sr.setSeed(seed);
        kgen.init(128, sr); //256 bits or 128 bits,192bits
        //AES中128位密钥版本有10个加密循环，192比特密钥版本有12个加密循环，256比特密钥版本则有14个加密循环。
        SecretKey skey = kgen.generateKey();
        return skey.getEncoded();
    }

    /*
     * 加密
     */
    public static String encrypt(String key, String content) {
        if (TextUtils.isEmpty(content)) {
            return content;
        }

        try {
            byte[] result = encrypt(key, content.getBytes());
            return Base64.encodeToString(result, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] encrypt(byte[] key, String content) {
        return encrypt(key, content, null);
    }

    public static byte[] encrypt(byte[] key, String content, IvParameterSpec ivSpec) {
        if (TextUtils.isEmpty(content)) {
            return null;
        }

        try {
            SecretKeySpec skeySpec = new SecretKeySpec(key, AES);
            Cipher cipher = Cipher.getInstance(CBC_PKCS5_PADDING);
            if (ivSpec == null) {
                ivSpec = new IvParameterSpec(new byte[cipher.getBlockSize()]);
            }

            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);
            return cipher.doFinal(content.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /*
     * 加密
     */
    private static byte[] encrypt(String key, byte[] content) throws Exception {
        byte[] raw = Base64.decode(key, Base64.NO_WRAP);
        SecretKeySpec skeySpec = new SecretKeySpec(raw, AES);
        Cipher cipher = Cipher.getInstance(CBC_PKCS5_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(new byte[cipher.getBlockSize()]));
        return cipher.doFinal(content);
    }

    /*
     * 解密
     */
    @Nullable
    public static String decrypt(String key, String encrypted, @Nullable IvParameterSpec ivSpec) {
        if (TextUtils.isEmpty(encrypted)) {
            return encrypted;
        }

        try {
            byte[] enc = Base64.decode(encrypted, Base64.NO_WRAP);
            byte[] result = decrypt(key, enc, ivSpec);
            return new String(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /*
     * 解密
     */
    public static byte[] decrypt(String key, byte[] encrypted) throws Exception {
        return decrypt(key, encrypted, null);
    }

    public static byte[] decrypt(String key, byte[] encrypted, @Nullable IvParameterSpec ivSpec) throws Exception {
        byte[] raw = Base64.decode(key, Base64.NO_WRAP);
        SecretKeySpec skeySpec = new SecretKeySpec(raw, AES);
        Cipher cipher = Cipher.getInstance(CBC_PKCS5_PADDING);
        if (ivSpec == null) {
            ivSpec = new IvParameterSpec(new byte[cipher.getBlockSize()]);
        }
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
        return cipher.doFinal(encrypted);

    }

    // 增加  CryptoProvider  类
    public static class CryptoProvider extends Provider {
        /**
         * Creates a Provider and puts parameters
         */
        public CryptoProvider() {
            super("Crypto", 1.0, "HARMONY (SHA1 digest; SecureRandom; SHA1withDSA signature)");
            put("SecureRandom.SHA1PRNG",
                    "org.apache.harmony.security.provider.crypto.SHA1PRNG_SecureRandomImpl");
            put("SecureRandom.SHA1PRNG ImplementedIn", "Software");
        }
    }
}
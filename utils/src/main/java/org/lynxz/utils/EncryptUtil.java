package org.lynxz.utils;

import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class EncryptUtil {
    public static final String SHA1 = "SHA-1";
    public static final String SHA256 = "SHA-256";

    /**
     * @param srcData 明文
     * @param key     密钥
     */
    @Keep
    @Nullable
    public static String aesEncrypt(@NonNull String srcData, String key) {
        return AESUtil.encrypt(key, srcData);
    }

    /**
     * 对元数据进行sha256摘要处理
     */
    @Keep
    @Nullable
    public static byte[] sha256(@NonNull byte[] srcData) {
        return encryptToByte(srcData, EncryptUtil.SHA256);
    }

    /**
     * 对字符串加密,加密算法使用MD5,SHA-1,SHA-256,默认使用SHA-256
     *
     * @param strSrc  要加密的字符串
     * @param encName 加密类型
     */
    public static String encrypt(String strSrc, String encName) {
        return encrypt(strSrc.getBytes(), encName);
    }


    public static String encrypt(byte[] strSrc, String encName) {
        byte[] result = encryptToByte(strSrc, encName);
        if (result == null || result.length == 0) {
            return null;
        }
        return ByteUtil.bytesToHexString(result);
    }


    public static byte[] encryptToByte(byte[] strSrc, String encName) {
        if (strSrc == null || strSrc.length == 0) {
            return null;
        }

        try {
            if (TextUtils.isEmpty(encName)) {
                encName = SHA256;
            }

            MessageDigest md = MessageDigest.getInstance(encName);
            md.update(strSrc);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}
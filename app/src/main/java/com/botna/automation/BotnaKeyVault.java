package com.botna.automation;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts user-supplied provider credentials with an Android Keystore AES key. */
public final class BotnaKeyVault {
    private static final String ALIAS = "botna_ai_api_key_v1";
    private static final String PREFS = "botna_settings";
    private static final String KEY = "ai_api_key_encrypted_v1";

    private BotnaKeyVault() { }

    public static synchronized void save(Context context, String apiKey) throws Exception {
        String value = apiKey == null ? "" : apiKey.trim();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (value.isEmpty()) {
            prefs.edit().remove(KEY).commit();
            return;
        }
        SecretKey secret = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + encrypted.length);
        buffer.put((byte) iv.length).put(iv).put(encrypted);
        String stored = Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
        if (!prefs.edit().putString(KEY, stored).commit()) {
            throw new IllegalStateException("تعذر حفظ مفتاح الذكاء الاصطناعي محليًا");
        }
    }

    public static synchronized String load(Context context) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        if (stored == null || stored.isEmpty()) return "";
        try {
            byte[] bytes = Base64.decode(stored, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            int ivLength = buffer.get() & 0xff;
            if (ivLength < 8 || ivLength > 32 || buffer.remaining() <= ivLength) return "";
            byte[] iv = new byte[ivLength]; buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String mask(Context context) {
        return load(context).isEmpty() ? "لا يوجد مفتاح محفوظ" : "يوجد مفتاح محفوظ ومشفّر على هذا الجهاز";
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}

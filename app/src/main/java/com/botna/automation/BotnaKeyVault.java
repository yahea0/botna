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

/**
 * Encrypts provider credentials with Android Keystore. Each provider profile gets
 * its own encrypted preference and Keystore alias so changing providers never
 * overwrites another provider's key.
 */
public final class BotnaKeyVault {
    private static final String LEGACY_ALIAS = "botna_ai_api_key_v1";
    private static final String V2_ALIAS_PREFIX = "botna_ai_key_v2_";
    private static final String PREFS = "botna_settings";
    private static final String LEGACY_KEY = "ai_api_key_encrypted_v1";
    private static final String V2_KEY_PREFIX = "ai_api_key_encrypted_v2_";

    private BotnaKeyVault() { }

    /** Backwards-compatible legacy API used by older builds. */
    public static synchronized void save(Context context, String apiKey) throws Exception {
        saveEncrypted(context, LEGACY_KEY, LEGACY_ALIAS, apiKey);
    }

    /** Save a key for one provider profile without touching other profiles. */
    public static synchronized void save(Context context, String profileId, String apiKey) throws Exception {
        String id = safeId(profileId);
        saveEncrypted(context, V2_KEY_PREFIX + id, V2_ALIAS_PREFIX + id, apiKey);
        // If an older build stored an OpenAI key in the legacy slot, clearing the
        // OpenAI profile must clear both slots rather than silently exposing it again.
        if ("openai_responses".equals(id) && (apiKey == null || apiKey.trim().isEmpty())) {
            saveEncrypted(context, LEGACY_KEY, LEGACY_ALIAS, "");
        }
    }

    /** Backwards-compatible legacy key loader. */
    public static synchronized String load(Context context) {
        return loadEncrypted(context, LEGACY_KEY, LEGACY_ALIAS);
    }

    /** Loads a provider-specific key; old OpenAI keys are migrated lazily. */
    public static synchronized String load(Context context, String profileId) {
        String id = safeId(profileId);
        String value = loadEncrypted(context, V2_KEY_PREFIX + id, V2_ALIAS_PREFIX + id);
        if (!value.isEmpty()) return value;
        if ("openai_responses".equals(id)) return load(context);
        return "";
    }

    public static synchronized boolean hasKey(Context context, String profileId) {
        return !load(context, profileId).isEmpty();
    }

    public static String mask(Context context) {
        String provider = BotnaAICompiler.providerId(context);
        return hasKey(context, provider)
                ? "يوجد مفتاح محفوظ ومشفّر لهذا المزوّد على هذا الجهاز"
                : "لا يوجد مفتاح محفوظ لهذا المزوّد";
    }

    private static void saveEncrypted(Context context, String prefKey, String alias, String apiKey) throws Exception {
        String value = apiKey == null ? "" : apiKey.trim();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (value.isEmpty()) {
            if (!prefs.edit().remove(prefKey).commit()) {
                throw new IllegalStateException("تعذر حذف مفتاح الذكاء الاصطناعي من التخزين المحلي");
            }
            return;
        }
        SecretKey secret = getOrCreateKey(alias);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + encrypted.length);
        buffer.put((byte) iv.length).put(iv).put(encrypted);
        String stored = Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
        if (!prefs.edit().putString(prefKey, stored).commit()) {
            throw new IllegalStateException("تعذر حفظ مفتاح الذكاء الاصطناعي محليًا");
        }
    }

    private static String loadEncrypted(Context context, String prefKey, String alias) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(prefKey, "");
        if (stored == null || stored.isEmpty()) return "";
        try {
            byte[] bytes = Base64.decode(stored, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            int ivLength = buffer.get() & 0xff;
            if (ivLength < 8 || ivLength > 32 || buffer.remaining() <= ivLength) return "";
            byte[] iv = new byte[ivLength]; buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static SecretKey getOrCreateKey(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(alias, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String safeId(String profileId) {
        String value = profileId == null ? "default" : profileId.trim().toLowerCase(java.util.Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_]+", "_");
        if (value.isEmpty()) value = "default";
        if (value.length() > 50) value = value.substring(0, 50);
        return value;
    }
}

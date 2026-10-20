package com.botna.automation;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class BotnaKeyVault {
    private static final String LEGACY_ALIAS = "botna_ai_api_key_v1";
    private static final String V2_ALIAS_PREFIX = "botna_ai_key_v2_";
    private static final String V3_ALIAS_PREFIX = "botna_ai_key_v3_";
    private static final String PREFS = "botna_settings";
    private static final String LEGACY_KEY = "ai_api_key_encrypted_v1";
    private static final String V2_KEY_PREFIX = "ai_api_key_encrypted_v2_";
    private static final String V3_KEYS_PREFIX = "ai_api_keys_encrypted_v3_";
    private static final String ACTIVE_PREFIX = "ai_api_key_active_v3_";
    private static final int MAX_KEYS = 20;

    private BotnaKeyVault() { }

    public static synchronized void save(Context context, String apiKey) throws Exception {
        saveEncrypted(context, LEGACY_KEY, LEGACY_ALIAS, apiKey);
    }

    public static synchronized void save(Context context, String profileId, String apiKey) throws Exception {
        String id = safeId(profileId);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            saveAll(context, id, new ArrayList<String>());
        } else {
            ArrayList<String> keys = new ArrayList<>();
            keys.add(apiKey.trim());
            saveAll(context, id, keys);
        }
    }

    public static synchronized void saveAll(Context context, String profileId, List<String> input) throws Exception {
        String id = safeId(profileId);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (input != null) for (String candidate : input) {
            String key = candidate == null ? "" : candidate.trim();
            if (!key.isEmpty()) unique.add(key);
            if (unique.size() >= MAX_KEYS) break;
        }
        JSONArray encrypted = new JSONArray();
        int index = 0;
        for (String key : unique) {
            encrypted.put(encryptValue(V3_ALIAS_PREFIX + id + "_" + index, key));
            index++;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (encrypted.length() == 0) {
            editor.remove(V3_KEYS_PREFIX + id).remove(ACTIVE_PREFIX + id).remove(V2_KEY_PREFIX + id);
            if ("openai_responses".equals(id)) editor.remove(LEGACY_KEY);
        } else {
            editor.putString(V3_KEYS_PREFIX + id, encrypted.toString());
            int active = prefs.getInt(ACTIVE_PREFIX + id, 0);
            editor.putInt(ACTIVE_PREFIX + id, Math.max(0, Math.min(encrypted.length() - 1, active)));
            editor.remove(V2_KEY_PREFIX + id);
            if ("openai_responses".equals(id)) editor.remove(LEGACY_KEY);
        }
        if (!editor.commit()) throw new IllegalStateException("تعذر حفظ مفاتيح هذا المزوّد مشفّرة.");
    }

    public static synchronized ArrayList<String> loadAll(Context context, String profileId) {
        String id = safeId(profileId);
        ArrayList<String> keys = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(V3_KEYS_PREFIX + id, "");
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray values = new JSONArray(raw);
                for (int i = 0; i < values.length(); i++) {
                    String key = decryptValue(V3_ALIAS_PREFIX + id + "_" + i, values.optString(i, ""));
                    if (!key.isEmpty() && !keys.contains(key)) keys.add(key);
                }
            } catch (JSONException ignored) { }
        }
        if (!keys.isEmpty()) return keys;
        String legacy = loadEncrypted(context, V2_KEY_PREFIX + id, V2_ALIAS_PREFIX + id);
        if (legacy.isEmpty() && "openai_responses".equals(id)) legacy = load(context);
        if (!legacy.isEmpty()) keys.add(legacy);
        return keys;
    }

    public static synchronized String load(Context context, String profileId) {
        ArrayList<String> keys = loadAll(context, profileId);
        if (keys.isEmpty()) return "";
        int index = activeIndex(context, profileId, keys.size());
        return keys.get(index);
    }

    public static synchronized int count(Context context, String profileId) { return loadAll(context, profileId).size(); }

    public static synchronized int activeIndex(Context context, String profileId, int size) {
        if (size <= 0) return 0;
        String id = safeId(profileId);
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(ACTIVE_PREFIX + id, 0);
        return Math.max(0, Math.min(size - 1, value));
    }

    public static synchronized void setActiveIndex(Context context, String profileId, int index) {
        String id = safeId(profileId);
        int size = loadAll(context, id).size();
        if (size <= 0) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(ACTIVE_PREFIX + id, Math.max(0, Math.min(size - 1, index))).apply();
    }

    public static synchronized void noteSuccessfulKey(Context context, String profileId, String apiKey) {
        ArrayList<String> keys = loadAll(context, profileId);
        if (apiKey == null) return;
        for (int i = 0; i < keys.size(); i++) if (apiKey.equals(keys.get(i))) {
            setActiveIndex(context, profileId, i); return;
        }
    }

    public static synchronized boolean hasKey(Context context, String profileId) {
        return count(context, profileId) > 0;
    }

    public static synchronized String load(Context context) {
        return loadEncrypted(context, LEGACY_KEY, LEGACY_ALIAS);
    }

    public static String mask(Context context) {
        String provider = BotnaAICompiler.providerId(context);
        int count = count(context, provider);
        return count > 0 ? "يوجد " + count + " مفتاح/مفاتيح محفوظة ومشفّرة لهذا المزوّد" : "لا يوجد مفتاح محفوظ لهذا المزوّد";
    }

    private static void saveEncrypted(Context context, String prefKey, String alias, String apiKey) throws Exception {
        String value = apiKey == null ? "" : apiKey.trim();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (value.isEmpty()) {
            if (!prefs.edit().remove(prefKey).commit()) throw new IllegalStateException("تعذر حذف مفتاح الذكاء الاصطناعي من التخزين المحلي");
            return;
        }
        String stored = encryptValue(alias, value);
        if (!prefs.edit().putString(prefKey, stored).commit()) throw new IllegalStateException("تعذر حفظ مفتاح الذكاء الاصطناعي محليًا");
    }

    private static String encryptValue(String alias, String value) throws Exception {
        SecretKey secret = getOrCreateKey(alias);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + encrypted.length);
        buffer.put((byte) iv.length).put(iv).put(encrypted);
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
    }

    private static String loadEncrypted(Context context, String prefKey, String alias) {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(prefKey, "");
        return decryptValue(alias, stored);
    }

    private static String decryptValue(String alias, String stored) {
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
        } catch (Exception ignored) { return ""; }
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
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private static String safeId(String profileId) {
        String value = profileId == null ? "default" : profileId.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_]+", "_");
        if (value.isEmpty()) value = "default";
        if (value.length() > 50) value = value.substring(0, 50);
        return value;
    }
}

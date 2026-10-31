package com.digitalcare.app;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Copies the public int/long/boolean/String/int[] fields of Settings and DayState to and from JSON. */
final class Json {
    private Json() {}

    static JSONObject write(Object o) throws JSONException {
        JSONObject j = new JSONObject();
        try {
            for (Field f : o.getClass().getFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object v = f.get(o);
                if (v instanceof int[]) {
                    JSONArray a = new JSONArray();
                    for (int i : (int[]) v) a.put(i);
                    v = a;
                }
                j.put(f.getName(), v == null ? JSONObject.NULL : v);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return j;
    }

    /** Fields whose key is missing or null keep their current value; a wrong type throws. */
    static <T> T read(JSONObject j, T into) throws JSONException {
        try {
            for (Field f : into.getClass().getFields()) {
                String k = f.getName();
                if (Modifier.isStatic(f.getModifiers()) || !j.has(k) || j.isNull(k)) continue;
                Class<?> t = f.getType();
                if (t == int.class) f.setInt(into, j.getInt(k));
                else if (t == long.class) f.setLong(into, j.getLong(k));
                else if (t == boolean.class) f.setBoolean(into, j.getBoolean(k));
                else if (t == String.class) f.set(into, j.getString(k));
                else if (t == int[].class) {
                    JSONArray a = j.getJSONArray(k);
                    int[] v = new int[a.length()];
                    for (int i = 0; i < v.length; i++) v[i] = a.getInt(i);
                    f.set(into, v);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return into;
    }
}

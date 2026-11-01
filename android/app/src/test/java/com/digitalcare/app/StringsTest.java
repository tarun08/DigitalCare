package com.digitalcare.app;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** The reminder texts exist in English and Hindi. A Hindi string missing a placeholder would crash a notification. */
public class StringsTest {
    /** Every string by name, and every plural form as name#quantity. The unit tests run in android/app. */
    private static Map<String, String> read(String path) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(path));
        Map<String, String> out = new LinkedHashMap<>();
        NodeList strings = doc.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element e = (Element) strings.item(i);
            out.put(e.getAttribute("name"), e.getTextContent());
        }
        NodeList plurals = doc.getElementsByTagName("plurals");
        for (int i = 0; i < plurals.getLength(); i++) {
            Element p = (Element) plurals.item(i);
            NodeList items = p.getElementsByTagName("item");
            for (int j = 0; j < items.getLength(); j++) {
                Element item = (Element) items.item(j);
                out.put(p.getAttribute("name") + "#" + item.getAttribute("quantity"), item.getTextContent());
            }
        }
        return out;
    }

    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = Pattern.compile("%\\d+\\$[sd]").matcher(text);
        while (m.find()) found.add(m.group());
        Collections.sort(found);
        return found;
    }

    @Test
    public void everyReminderStringHasAHindiTwinWithTheSamePlaceholders() throws Exception {
        Map<String, String> en = read("src/main/res/values/strings.xml");
        Map<String, String> hi = read("src/main/res/values-hi/strings.xml");
        int checked = 0;
        for (Map.Entry<String, String> e : en.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("n_") && !key.startsWith("tip_")) continue;      // the app name and the like stay as they are
            checked++;
            assertTrue("no Hindi for " + key, hi.containsKey(key));
            assertEquals("placeholders of " + key, placeholders(e.getValue()), placeholders(hi.get(key)));
            assertTrue(key + " is not in Hindi", hi.get(key).codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F));
        }
        assertTrue("found no reminder strings to check", checked >= 16);
    }

    @Test
    public void thePhoneTextSaysMinuteForOneAndMinutesForMore() throws Exception {   // the limit can be set to 1
        Map<String, String> en = read("src/main/res/values/strings.xml");
        assertTrue("no singular form", en.containsKey("n_phone_text#one"));
        assertTrue(en.get("n_phone_text#one").contains("%1$d minute in a row"));
        assertTrue(en.get("n_phone_text#other").contains("%1$d minutes in a row"));
    }
}

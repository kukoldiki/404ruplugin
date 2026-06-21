package main.java.esco.bundle;

import arc.struct.ObjectMap;
import arc.struct.StringMap;
import arc.util.Log;
import mindustry.mod.Mod;

import java.util.Locale;
import java.util.ResourceBundle;

import static mindustry.Vars.mods;


/**
 * Chaos of bad code
 */
public class Bundle {
    public static final ObjectMap<Locale, StringMap> bundles = new ObjectMap<>();
    public static StringMap defaultBundle;

    public static void load(Class<? extends Mod> modName) {
        mods.getMod(modName).root.child("bundles").walk(f -> {
            var codes = f.nameWithoutExtension().split("_");
            Locale locale = new Locale(codes.length == 1 ? "en" : codes[1]);
            var bundle = ResourceBundle.getBundle("bundles.bundle", locale);
            bundle.keySet().forEach(key -> bundles.get(locale, StringMap::new).put(key, bundle.getString(key)));
            if (codes.length == 1)
                defaultBundle = bundles.get(locale);
        });
    }
    //TODO. Плагин не умеет работать с локалями имеющими _, например uk_ua, он выдаст за английский.

    /**
     * Get localized text
     *
     * @param request   bundle name
     * @param localeStr locale in string
     * @return String localized text
     */
    public static String get(String request, String localeStr) {
        Locale locale = null;
        if (localeStr.contains("_")) {
            String[] parts = localeStr.split("_");
            locale = new Locale(parts[0], parts[1]);
        } else {
            locale = new Locale(localeStr);
        }
        Log.debug(locale);
        StringMap bundle = bundles.get(locale);
        if (bundle == null) {
            Locale languageOnlyLocale = new Locale(locale.getLanguage());
            bundle = bundles.get(languageOnlyLocale);
        }
        if (bundle == null) {
            bundle = defaultBundle;
        }
        String res = bundle.get(request, defaultBundle.get(request, request));

        if (res.equals(request)) {
            Log.warn(request + " без бандла, локаль " + localeStr);
        }
        return res;
    }
}

package org.vosk.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class LanguageModelDefinition {
    private final String id;
    private final String localeName;
    private final URL url;

    private LanguageModelDefinition(Object key, Object value) {
        this.id = key.toString();
        String[] values = value.toString().split(",");
        this.localeName = values[0];
        URL url;
        try {
            url = new URL(values[1]);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            url = null;
        }
        this.url = url;
    }
    public String getId() {
        return id;
    }

    public String getLocaleName() {
        return localeName;
    }

    public URL getUrl() {
        return url;
    }
    public String getModelId() {
        String path = getUrl().getPath();
        int start = path.lastIndexOf('/');
        int end = path.lastIndexOf('.');
        if (start > 0 && end > start) {
            return path.substring(start +1 , end);
        }
        return path;
    }

    public static Map<String, LanguageModelDefinition> getLanguages(InputStream in) throws IOException {
        Properties properties = new Properties();
        // note: Properties standard use iso-8859-1. However this app uses utf8
        properties.load(new InputStreamReader(in, "UTF8"));
        return getLanguages(properties);
    }

    public static Map<String, LanguageModelDefinition> getLanguages(Properties properties) {
        return properties.entrySet()
                .stream()
                .map(s -> new LanguageModelDefinition(s.getKey(), s.getValue()))
                .collect(Collectors.toMap(l -> l.getId(), l -> l));
    }
}

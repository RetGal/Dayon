package mpo.dayon.common.utils;

public enum Language {
    DE("de", "Deutsch"),
    EN("en", "English"),
    ES("es", "Español"),
    FR("fr", "Français"),
    IT("it", "Italiano"),
    RU("ru", "Русский"),
    SV("sv", "Svenska"),
    TR("tr", "Türkçe"),
    ZH("zh", "简体中文");

    private final String shortName;
    private final String name;

    Language(String shortName, String name) {
        this.shortName = shortName;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

}

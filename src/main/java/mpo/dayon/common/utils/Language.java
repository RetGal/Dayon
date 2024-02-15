package mpo.dayon.common.utils;

public enum Language {
    de("de", "Deutsch"),
    en("en", "English"),
    es("es", "Español"),
    fr("fr", "Français"),
    it("it", "Italiano"),
    ru("ru", "Русский"),
    sv("sv", "Svenska"),
    tr("tr", "Türkçe"),
    zh("zh", "简体中文");

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

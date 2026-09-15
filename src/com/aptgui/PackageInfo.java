package com.aptgui;

/**
 * 软件包信息模型，封装 apt-cache 搜索与查询结果。
 */
public class PackageInfo {
    private final String name;
    private final String version;
    private final String section;
    private final String architecture;
    private final String installedSize;
    private final String depends;
    private final String description;
    private final String longDescription;
    private final boolean installed;

    public PackageInfo(String name, String version, String section,
                       String architecture, String installedSize,
                       String depends, String description,
                       String longDescription, boolean installed) {
        this.name = name;
        this.version = version;
        this.section = section;
        this.architecture = architecture;
        this.installedSize = installedSize;
        this.depends = depends;
        this.description = description;
        this.longDescription = longDescription;
        this.installed = installed;
    }

    /** 仅用于搜索结果列表的简化构造 */
    public PackageInfo(String name, String description) {
        this(name, "—", "—", "—", "—", "—", description, "", false);
    }

    /** 已安装软件包列表构造（包名 + 版本） */
    public PackageInfo(String name, String version, boolean installed) {
        this(name, version, "—", "—", "—", "—", "", "", installed);
    }

    public String getName()             { return name; }
    public String getVersion()          { return version; }
    public String getSection()          { return section; }
    public String getArchitecture()     { return architecture; }
    public String getInstalledSize()    { return installedSize; }
    public String getDepends()          { return depends; }
    public String getDescription()      { return description; }
    public String getLongDescription()  { return longDescription; }
    public boolean isInstalled()        { return installed; }

    @Override
    public String toString() {
        return name + " — " + description;
    }
}

package ru.privatenull.pnrelog.api.region;

public interface RegionApi {
    void setPolicy(RegionPolicy policy);

    RegionPolicy getPolicy();

    String activeProvider();
}

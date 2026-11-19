package com.techgenius.tvoslauncher;

/** Single source of the app version: build.sh / build.ps1 / CI read it from this file. */
final class BuildVersion {
    static final String NAME = "3.0.0";
    static final int CODE = 300;

    private BuildVersion() {
    }
}

package zx.offical.quattro.mods;

import android.graphics.Bitmap;

import java.io.File;

public class LocalModFile {
    public File file;
    public final String fileName;
    public final String displayName;
    public final String description;
    public final Bitmap icon;
    public boolean enabled;

    public LocalModFile(File file, String fileName, String displayName, String description, Bitmap icon, boolean enabled) {
        this.file = file;
        this.fileName = fileName;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.enabled = enabled;
    }
}

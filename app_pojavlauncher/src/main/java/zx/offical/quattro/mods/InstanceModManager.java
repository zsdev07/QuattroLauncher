package zx.offical.quattro.mods;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import zx.offical.quattro.JMinecraftVersionList;
import zx.offical.quattro.Tools;
import zx.offical.quattro.instances.Instance;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class InstanceModManager {
    private static final String TAG = "InstanceModManager";
    private static final String DISABLED_SUFFIX = ".disabled";
    private static final Pattern TOML_STRING_PATTERN = Pattern.compile("^%s\\s*=\\s*\"(.*)\"\\s*$");

    private InstanceModManager() {}

    public static ModSupportInfo getSupportInfo(Instance instance) {
        File gameDir = instance.getGameDirectory();
        File modsDir = new File(gameDir, "mods");
        if (isOptiFineInstance(instance.versionId)) {
            return new ModSupportInfo(false, false, modsDir, "OptiFine instances do not support local mod toggles.");
        }
        if (isSupportedModdedInstance(instance.versionId)) {
            return new ModSupportInfo(true, true, modsDir, null);
        }
        if (hasAnyModFiles(modsDir)) {
            return new ModSupportInfo(true, true, modsDir, null);
        }
        return new ModSupportInfo(false, false, modsDir, "Available only for Forge, NeoForge, Fabric, Quilt, or modpack instances with a mods folder.");
    }

    public static List<LocalModFile> scanMods(Instance instance) {
        ModSupportInfo supportInfo = getSupportInfo(instance);
        if (!supportInfo.isSupported) return Collections.emptyList();

        File[] files = supportInfo.modsDirectory.listFiles(file -> file.isFile()
                && (file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
                || file.getName().toLowerCase(Locale.ROOT).endsWith(".jar" + DISABLED_SUFFIX)));
        if (files == null || files.length == 0) return Collections.emptyList();

        ArrayList<LocalModFile> mods = new ArrayList<>(files.length);
        for (File file : files) {
            try {
                mods.add(readMetadata(file));
            } catch (Exception e) {
                Log.w(TAG, "Failed to read metadata for " + file.getName(), e);
                mods.add(createFallback(file, isEnabled(file)));
            }
        }
        mods.sort((left, right) -> {
            int enabledCompare = Boolean.compare(right.enabled, left.enabled);
            if (enabledCompare != 0) return enabledCompare;
            return left.displayName.compareToIgnoreCase(right.displayName);
        });
        return mods;
    }

    public static void setModEnabled(LocalModFile mod, boolean enabled) throws IOException {
        if (mod.enabled == enabled) return;
        File source = mod.file;
        File target = enabled ? toEnabledFile(source) : toDisabledFile(source);
        if (target.exists()) throw new IOException("Target already exists: " + target.getName());
        if (!source.renameTo(target)) throw new IOException("Failed to rename " + source.getName());
        mod.file = target;
        mod.enabled = enabled;
    }

    private static LocalModFile readMetadata(File file) throws IOException {
        boolean enabled = isEnabled(file);
        String fallbackName = stripModExtension(file.getName());
        Metadata metadata = new Metadata(fallbackName, "No description available", null);

        try (ZipFile zipFile = new ZipFile(file)) {
            metadata = parseFabricMetadata(zipFile, metadata);
            metadata = parseQuiltMetadata(zipFile, metadata);
            metadata = parseForgeMetadata(zipFile, metadata);
            metadata = parseLegacyMetadata(zipFile, metadata);
        }
        return new LocalModFile(file, file.getName(), metadata.name, metadata.description, metadata.icon, enabled);
    }

    private static Metadata parseFabricMetadata(ZipFile zipFile, Metadata current) {
        ZipEntry entry = zipFile.getEntry("fabric.mod.json");
        if (entry == null) return current;
        try {
            JsonObject root = readJsonObject(zipFile, entry);
            String name = getString(root, "name", current.name);
            String description = getString(root, "description", current.description);
            Bitmap icon = readIcon(zipFile, extractIconPath(root.get("icon")));
            return current.merge(name, description, icon);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse fabric metadata", e);
            return current;
        }
    }

    private static Metadata parseQuiltMetadata(ZipFile zipFile, Metadata current) {
        ZipEntry entry = zipFile.getEntry("quilt.mod.json");
        if (entry == null) return current;
        try {
            JsonObject root = readJsonObject(zipFile, entry);
            JsonObject quiltLoader = getObject(root, "quilt_loader");
            JsonObject metadataObject = quiltLoader == null ? null : getObject(quiltLoader, "metadata");
            String name = metadataObject == null ? current.name : getString(metadataObject, "name", current.name);
            String description = metadataObject == null ? current.description : getString(metadataObject, "description", current.description);
            JsonElement iconElement = metadataObject == null ? null : metadataObject.get("icon");
            Bitmap icon = readIcon(zipFile, extractIconPath(iconElement));
            return current.merge(name, description, icon);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse quilt metadata", e);
            return current;
        }
    }

    private static Metadata parseForgeMetadata(ZipFile zipFile, Metadata current) {
        ZipEntry entry = zipFile.getEntry("META-INF/mods.toml");
        if (entry == null) return current;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            boolean insideModsBlock = false;
            String name = current.name;
            String description = current.description;
            String logoFile = null;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (trimmed.startsWith("[[mods]]")) {
                    if (insideModsBlock) break;
                    insideModsBlock = true;
                    continue;
                }
                if (!insideModsBlock) continue;
                name = matchTomlValue(trimmed, "displayName", name);
                description = matchTomlValue(trimmed, "description", description);
                logoFile = matchTomlValue(trimmed, "logoFile", logoFile);
            }
            Bitmap icon = readIcon(zipFile, logoFile);
            return current.merge(name, description, icon);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse forge metadata", e);
            return current;
        }
    }

    private static Metadata parseLegacyMetadata(ZipFile zipFile, Metadata current) {
        ZipEntry entry = zipFile.getEntry("mcmod.info");
        if (entry == null) return current;
        try {
            JsonElement root = new JsonParser().parse(readZipEntry(zipFile, entry));
            JsonObject modInfo = null;
            if (root.isJsonArray()) {
                JsonArray array = root.getAsJsonArray();
                if (array.size() > 0 && array.get(0).isJsonObject()) modInfo = array.get(0).getAsJsonObject();
            } else if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                JsonArray modList = object.getAsJsonArray("modList");
                if (modList != null && modList.size() > 0 && modList.get(0).isJsonObject()) {
                    modInfo = modList.get(0).getAsJsonObject();
                }
            }
            if (modInfo == null) return current;
            String name = getString(modInfo, "name", current.name);
            String description = getString(modInfo, "description", current.description);
            return current.merge(name, description, null);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse mcmod.info metadata", e);
            return current;
        }
    }

    private static JsonObject readJsonObject(ZipFile zipFile, ZipEntry entry) throws IOException {
        JsonElement element = new JsonParser().parse(readZipEntry(zipFile, entry));
        return element.getAsJsonObject();
    }

    private static String readZipEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream inputStream = zipFile.getInputStream(entry);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = bufferedReader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }

    private static Bitmap readIcon(ZipFile zipFile, String iconPath) {
        if (!Tools.isValidString(iconPath)) return null;
        String normalized = iconPath.startsWith("/") ? iconPath.substring(1) : iconPath;
        ZipEntry iconEntry = zipFile.getEntry(normalized);
        if (iconEntry == null) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equalsIgnoreCase(normalized)) {
                    iconEntry = entry;
                    break;
                }
            }
        }
        if (iconEntry == null) return null;
        try (InputStream inputStream = zipFile.getInputStream(iconEntry)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            Log.w(TAG, "Failed to decode icon " + normalized, e);
            return null;
        }
    }

    private static String extractIconPath(JsonElement iconElement) {
        if (iconElement == null || iconElement.isJsonNull()) return null;
        if (iconElement.isJsonPrimitive()) return iconElement.getAsString();
        if (!iconElement.isJsonObject()) return null;
        JsonObject iconObject = iconElement.getAsJsonObject();
        String bestPath = null;
        int bestSize = -1;
        for (java.util.Map.Entry<String, JsonElement> entry : iconObject.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive()) continue;
            try {
                int size = Integer.parseInt(key);
                if (size > bestSize) {
                    bestSize = size;
                    bestPath = value.getAsString();
                }
            } catch (NumberFormatException e) {
                if (bestPath == null) bestPath = value.getAsString();
            }
        }
        return bestPath;
    }

    private static String getString(JsonObject object, String key, String fallback) {
        if (object == null) return fallback;
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            String value = element.getAsString();
            return Tools.isValidString(value) ? value : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static JsonObject getObject(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || !element.isJsonObject()) return null;
        return element.getAsJsonObject();
    }

    private static String matchTomlValue(String line, String key, String fallback) {
        Matcher matcher = Pattern.compile(String.format(Locale.ROOT, TOML_STRING_PATTERN.pattern(), Pattern.quote(key))).matcher(line);
        if (!matcher.matches()) return fallback;
        String value = matcher.group(1).replace("\\n", " ").trim();
        return value.isEmpty() ? fallback : value;
    }

    private static LocalModFile createFallback(File file, boolean enabled) {
        String fallbackName = stripModExtension(file.getName());
        return new LocalModFile(file, file.getName(), fallbackName, "No description available", null, enabled);
    }

    private static String stripModExtension(String fileName) {
        String normalized = fileName;
        if (normalized.endsWith(DISABLED_SUFFIX)) normalized = normalized.substring(0, normalized.length() - DISABLED_SUFFIX.length());
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".jar")) normalized = normalized.substring(0, normalized.length() - 4);
        return normalized;
    }

    private static boolean isEnabled(File file) {
        return !file.getName().toLowerCase(Locale.ROOT).endsWith(DISABLED_SUFFIX);
    }

    private static File toDisabledFile(File file) {
        return new File(file.getParentFile(), file.getName() + DISABLED_SUFFIX);
    }

    private static File toEnabledFile(File file) {
        String name = file.getName();
        if (!name.toLowerCase(Locale.ROOT).endsWith(DISABLED_SUFFIX)) return file;
        return new File(file.getParentFile(), name.substring(0, name.length() - DISABLED_SUFFIX.length()));
    }

    private static boolean hasAnyModFiles(File modsDir) {
        File[] files = modsDir.listFiles(file -> file.isFile()
                && (file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
                || file.getName().toLowerCase(Locale.ROOT).endsWith(".jar" + DISABLED_SUFFIX)));
        return files != null && files.length > 0;
    }

    private static boolean isOptiFineInstance(String versionId) {
        return versionId != null && versionId.toLowerCase(Locale.ROOT).contains("optifine");
    }

    private static boolean isSupportedModdedInstance(String versionId) {
        if (!Tools.isValidString(versionId)) return false;
        String lowered = versionId.toLowerCase(Locale.ROOT);
        if (lowered.contains("fabric-loader") || lowered.contains("quilt-loader") || lowered.contains("neoforge-")) return true;
        if (lowered.contains("forge") && !lowered.contains("optifine")) return true;
        try {
            JMinecraftVersionList.Version version = Tools.getVersionInfo(versionId, false);
            if (version == null) return false;
            if (version.mainClass != null) {
                String mainClass = version.mainClass.toLowerCase(Locale.ROOT);
                if (mainClass.contains("fabric") || mainClass.contains("quilt") || mainClass.contains("forge")) return true;
            }
            if (version.libraries != null) {
                for (int i = 0; i < version.libraries.length; i++) {
                    String name = version.libraries[i].name;
                    if (name == null) continue;
                    String library = name.toLowerCase(Locale.ROOT);
                    if (library.startsWith("net.fabricmc:fabric-loader")
                            || library.startsWith("org.quiltmc:quilt-loader")
                            || library.startsWith("net.minecraftforge:forge")
                            || library.startsWith("net.neoforged:neoforge")) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to inspect version metadata for " + versionId, e);
        }
        return false;
    }

    public static final class ModSupportInfo {
        public final boolean isVisible;
        public final boolean isSupported;
        public final File modsDirectory;
        public final String reason;

        public ModSupportInfo(boolean isVisible, boolean isSupported, File modsDirectory, String reason) {
            this.isVisible = isVisible;
            this.isSupported = isSupported;
            this.modsDirectory = modsDirectory;
            this.reason = reason;
        }
    }

    private static final class Metadata {
        public final String name;
        public final String description;
        public final Bitmap icon;

        private Metadata(String name, String description, Bitmap icon) {
            this.name = name;
            this.description = description;
            this.icon = icon;
        }

        private Metadata merge(String candidateName, String candidateDescription, Bitmap candidateIcon) {
            String mergedName = Tools.isValidString(candidateName) ? candidateName : name;
            String mergedDescription = Tools.isValidString(candidateDescription) ? candidateDescription : description;
            Bitmap mergedIcon = candidateIcon != null ? candidateIcon : icon;
            return new Metadata(mergedName, mergedDescription, mergedIcon);
        }
    }
}

package net.minecraftforge.common.config;
import java.io.File;
public class Configuration {
    public Configuration(File file) {}
    public Property get(String category, String key, double def) { return null; }
    public Property get(String category, String key, int def) { return null; }
    public Property get(String category, String key, boolean def) { return null; }
    public boolean hasChanged() { return false; }
    public void save() {}
    public void addCustomCategoryComment(String category, String comment) {}
}

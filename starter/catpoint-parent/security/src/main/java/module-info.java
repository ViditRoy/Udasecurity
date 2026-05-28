module com.udacity.catpoint {
    requires java.desktop;
    requires com.udacity.image;
    requires com.google.common;
    requires com.google.gson;
    requires java.prefs;
    requires miglayout.swing;

    // Required for Gson to serialize/deserialize Sensor objects stored in user prefs
    opens com.udacity.catpoint.data to com.google.gson;
}

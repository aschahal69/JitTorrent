package com.jittorrent.util;

// java io library for getting file type data it is abstract class but it reads bytes from a file and converts them into characters
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


// this is a class that loads configuration data from a properties file and provides access to the loaded properties.
public class ConfigLoader {
    // private because this is personal info and dosent need to be accessed outside this class
    // static because we can just use exception to catch missing values later
    // final because we dont want to change the properties like the download folder for a file mid download
    private static final Properties PROPS = new Properties();

    //static initializer block loads the file once, and the private constructor stops anyone from creating instances of this class.
    static{
        
        try (

            // Load the properties file from the resources folder
            // new inputstream object is not created but it simply finds the file using classpath and loads it into the PROPS object
            InputStream in = ConfigLoader.class.getResourceAsStream("/config.properties");
        )
        {
            // basic exception handling could be better
            if (in == null) {
                throw new IOException("config.properties file not found in resources.");
            }

            //if check is good then load the read resource file in the properties object
            PROPS.load(in);

        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to load configuration properties", e);

        }

    }
    // private constructor to prevent a new instantiation of the ConfigLoader class
    private ConfigLoader() { }

    // method to get name of property stored
    public static String get(String key) {
    return System.getProperty(key, PROPS.getProperty(key));
}

public static int getInt(String key) {
    return Integer.parseInt(get(key));
}
    
}

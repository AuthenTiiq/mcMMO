package com.gmail.nossr50.config;

import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Handles loading and cacheing configuration settings from a configurable compatible config file
 */
public abstract class Config implements VersionedConfig {

    public static final String HOCON_FILE_EXTENSION = ".conf";
    public final File DIRECTORY_DATA_FOLDER; //Directory that the file is in
    public final String FILE_RELATIVE_PATH; //Relative Path to the file
    protected final String DIRECTORY_DEFAULTS = "defaults";
    /* SETTINGS */
    //private static final String FILE_EXTENSION = ".conf"; //HOCON
    private boolean mergeNewKeys; //Whether or not to merge keys found in the default config
    private boolean removeOldKeys; //Whether or not to remove unused keys form the config

    /* PATH VARS */
    private boolean copyDefaults; //Whether or not to copy the default config when first creating the file
    private boolean generateDefaults; //Whether or not we use Configurate to generate a default file, if this is false we copy the file from the JAR
    private String fileName; //The file name of the config

    /* LOADERS */
    private HoconConfigurationLoader defaultCopyLoader;
    private HoconConfigurationLoader userCopyLoader;

    //private ConfigurationLoader<CommentedCommentedConfigurationNode> defaultCopyLoader;
    //private ConfigurationLoader<CommentedCommentedConfigurationNode> userCopyLoader;

    /* CONFIG FILES */

    private File resourceConfigCopy; //Copy of the default config from the JAR (file is copied so that admins can easily compare to defaults)
    private File resourceUserCopy; //File in the /$MCMMO_ROOT/mcMMO/ directory that may contain user edited settings

    /* ROOT NODES */

    private CommentedConfigurationNode userRootNode = null;
    private CommentedConfigurationNode defaultRootNode = null;

    /* CONFIG MANAGER */
    //private ConfigurationLoader<CommentedCommentedConfigurationNode> configManager;

    /*public Config(String pathToParentFolder, String relativePath, boolean mergeNewKeys, boolean copyDefaults, boolean removeOldKeys) {
        //TODO: Check if this works...
        this(new File(pathToParentFolder), relativePath, mergeNewKeys, copyDefaults, removeOldKeys);
        System.out.println("mcMMO Debug: Don't forget to check if loading config file by string instead of File works...");
    }*/

    public Config(String fileName, File pathToParentFolder, String relativePath, boolean generateDefaults, boolean mergeNewKeys, boolean copyDefaults, boolean removeOldKeys) {
        mkdirDefaults(); // Make our default config dir

        /*
         * These must be at the top
         */
        this.fileName = fileName;
        this.generateDefaults = generateDefaults;
        this.copyDefaults = copyDefaults;
        this.mergeNewKeys = mergeNewKeys; //Whether or not we add new keys when they are found
        this.removeOldKeys = removeOldKeys;

        DIRECTORY_DATA_FOLDER = pathToParentFolder; //Data Folder for our plugin
        FILE_RELATIVE_PATH = relativePath + fileName + HOCON_FILE_EXTENSION; //Relative path to config from a parent folder
    }

    public void initFullConfig() {
        //Attempt IO Operations
        try {
            //Makes sure we have valid Files corresponding to this config
            initConfigFiles();

            //Init MainConfig Loaders
            initConfigLoaders();

            //Load MainConfig Nodes
            loadConfig();

            //Attempt to update user file, and then load it into memory
            readConfig();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Cleanup and backup registers
//        registerUnload();
        registerFileBackup();
    }

//    /**
//     * Registers with the config managers unloader
//     * The unloader runs when the plugin gets disabled which cleans up registries to make reloading safe
//     */
//    private void registerUnload() {
//        mcMMO.getConfigManager().registerUnloadable(this);
//    }

    /**
     * Registers with the config managers file list
     * Used for backing up configs with our zip library
     */
    private void registerFileBackup() {
        pluginRef.getConfigManager().registerUserFile(getUserConfigFile());
    }

    /**
     * Initializes the default copy File and the user config File
     *
     * @throws IOException
     */
    private void initConfigFiles() throws IOException {
        //Init our config copy
        resourceConfigCopy = initDefaultConfig();

        //Init the user file
        resourceUserCopy = initUserConfig();
    }

    /**
     * Loads the root node for the default config File and user config File
     */
    private void loadConfig() {
        try {
            final CommentedConfigurationNode defaultConfig = this.defaultCopyLoader.load();
            defaultRootNode = defaultConfig;

            final CommentedConfigurationNode userConfig = this.userCopyLoader.load();
            userRootNode = userConfig;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initializes the Configuration Loaders for this config
     */
    private void initConfigLoaders() {
        this.defaultCopyLoader = HoconConfigurationLoader.builder().setPath(resourceConfigCopy.toPath()).build();
        this.userCopyLoader = HoconConfigurationLoader.builder().setPath(resourceUserCopy.toPath()).build();
    }

    /**
     * Copies a new file from the JAR to the defaults directory and uses that new file to initialize our resourceConfigCopy
     *
     * @throws IOException
     * @see Config#resourceConfigCopy
     */
    private File initDefaultConfig() throws IOException {
        if (generateDefaults) {
            return generateDefaultFile();
        } else
            return copyDefaultFromJar(getDefaultConfigCopyRelativePath(), true);
    }

    /**
     * Generates a default config file using the Configurate library, makes use of @Setting and @ConfigSerializable annotations in the config file
     * Assigns the default root node to the newly loaded default config if successful
     *
     * @return the File for the newly created config
     */
    private File generateDefaultFile() {
        pluginRef.getLogger().info("Attempting to create a default config for " + fileName);

        //Not sure if this will work properly...
        Path potentialFile = Paths.get(getDefaultConfigCopyRelativePath());
        ConfigurationLoader<CommentedConfigurationNode> generation_loader
                = HoconConfigurationLoader.builder().setPath(potentialFile).build();

        try {
            pluginRef.getLogger().info("Config File Full Path: " + getDefaultConfigFile().getAbsolutePath());
            //Delete any existing default config
            if (getDefaultConfigFile().exists())
                getDefaultConfigFile().delete();

            //Make new file
            getDefaultConfigFile().createNewFile();

            //Load the config
            defaultRootNode = generation_loader.load();

            //Save to a new file
            generation_loader.save(defaultRootNode);

            pluginRef.getLogger().info("Generated a default file for " + fileName);
        } catch (IOException e) {
            pluginRef.getLogger().severe("Error when trying to generate a default configuration file for " + getDefaultConfigCopyRelativePath());
            e.printStackTrace();
        }

        //Return the default file
        return getDefaultConfigFile();
    }

    /**
     * Attemps to load the config file if it exists, if it doesn't it copies a new one from within the JAR
     *
     * @return user config File
     * @throws IOException
     * @see Config#resourceUserCopy
     */
    private File initUserConfig() throws IOException {
        File userCopy = new File(DIRECTORY_DATA_FOLDER, FILE_RELATIVE_PATH); //Load the user file;

        if (userCopy.exists()) {
            // Yay
            return userCopy;
        } else {
            //If it's gone we copy default files
            //Note that we don't copy the values from the default copy put in /defaults/ that file exists only as a reference to admins and is unreliable
            if (copyDefaults)
                return copyDefaultFromJar(FILE_RELATIVE_PATH, false);
            else {
                //Make a new empty file
                userCopy.createNewFile();
                return userCopy;
            }
        }
    }

    /**
     * Gets the File representation of the this users config
     *
     * @return the users config File
     */
    public File getUserConfigFile() {
        return new File(DIRECTORY_DATA_FOLDER, FILE_RELATIVE_PATH);
    }

    /**
     * Used to make a new config file at a specified relative output path inside the data directory by copying the matching file found in that same relative path within the JAR
     *
     * @param relativeOutputPath the path to the output file
     * @param deleteOld          whether or not to delete the existing output file on disk
     * @return a copy of the default config within the JAR
     * @throws IOException
     */
    private File copyDefaultFromJar(String relativeOutputPath, boolean deleteOld) throws IOException {
        /*
         * Gen a Default config from inside the JAR
         */
        pluginRef.getLogger().info("Preparing to copy internal resource file (in JAR) - " + FILE_RELATIVE_PATH);
        //InputStream inputStream = McmmoCore.getResource(FILE_RELATIVE_PATH);
        InputStream inputStream = pluginRef.getResource(FILE_RELATIVE_PATH);

        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);

        //This is a copy of the default file, which we will overwrite every time mcMMO loads
        File targetFile = new File(DIRECTORY_DATA_FOLDER, relativeOutputPath);

        //Wipe old default file on disk
        if (targetFile.exists() && deleteOld) {
            pluginRef.getLogger().info("Updating file " + relativeOutputPath);
            targetFile.delete(); //Necessary?
        }

        if (!targetFile.exists()) {
            targetFile.getParentFile().mkdirs();
            targetFile.createNewFile(); //New File Boys
        }

        Files.write(buffer, targetFile);
        pluginRef.getLogger().info("Created config file - " + relativeOutputPath);

        inputStream.close(); //Close the input stream

        return targetFile;
    }

    /**
     * The path to the defaults directory
     *
     * @return the path to the defaults directory
     */
    private String getDefaultConfigCopyRelativePath() {
        return getDefaultConfigFile().getPath();
    }

    /**
     * Grabs the File representation of the default config, which is stored on disk in a defaults folder
     * this file will be overwritten every time mcMMO starts to keep it up to date.
     *
     * @return the copy of the default config file, stored in the defaults directory
     */
    private File getDefaultConfigFile() {
        return new File(ConfigConstants.getDefaultsFolder(), FILE_RELATIVE_PATH);
    }

    /**
     * Creates the defaults directory
     */
    private void mkdirDefaults() {
        ConfigConstants.makeAllConfigDirectories();
    }

    /**
     * Configs are versioned based on when they had significant changes to keys
     *
     * @return current MainConfig Version String
     */
    public String getVersion() {
        return String.valueOf(getConfigVersion());
    }

    /**
     * Attempts to read the loaded config file
     * MainConfig will have any necessary updates applied
     * MainConfig will be compared to the default config to see if it is missing any nodes
     * MainConfig will have any missing nodes inserted with their default value
     */
    public void readConfig() {
        pluginRef.getLogger().info("Attempting to read " + FILE_RELATIVE_PATH + ".");

        int version = this.userRootNode.getNode("ConfigVersion").getInt();
        pluginRef.getLogger().info(FILE_RELATIVE_PATH + " version is " + version);

        //Update our config
        updateConfig();
    }

    /**
     * Compares the users config file to the default and adds any missing nodes and applies any necessary updates
     */
    private void updateConfig() {
        pluginRef.getLogger().info(defaultRootNode.getChildrenMap().size() + " items in default children map");
        pluginRef.getLogger().info(userRootNode.getChildrenMap().size() + " items in default root map");

        // Merge Values from default
        if (mergeNewKeys)
            userRootNode = userRootNode.mergeValuesFrom(defaultRootNode);

        removeOldKeys();

        // Update config version
        updateConfigVersion();

        //Attempt to save
        try {
            saveUserCopy();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Finds any keys in the users config that are not present in the default config and removes them
     */
    //TODO: Finish this
    private void removeOldKeys() {
        if (!removeOldKeys)
            return;

        for (CommentedConfigurationNode CommentedConfigurationNode : defaultRootNode.getChildrenList()) {

        }
    }

    /**
     * Saves the current state information of the config to the users copy (which they may edit)
     *
     * @throws IOException
     */
    private void saveUserCopy() throws IOException {
        pluginRef.getLogger().info("Saving new node");
        userCopyLoader.save(userRootNode);
    }

    /**
     * Performs any necessary operations to update this config
     */
    private void updateConfigVersion() {
        // Set a version for our config
        this.userRootNode.getNode("ConfigVersion").setValue(getConfigVersion());
        pluginRef.getLogger().info("Updated config to [" + getConfigVersion() + "] - " + FILE_RELATIVE_PATH);
    }

    /**
     * Returns the root node of this config
     *
     * @return the root node of this config
     */
    protected CommentedConfigurationNode getUserRootNode() {
        return userRootNode;
    }

    /**
     * Gets an int from the config and casts it to short before returning
     *
     * @param path the path to the int
     * @return the value of the int after being cast to short at the node, null references will zero initialize
     */
    public short getShortValue(String... path) {
        return (short) userRootNode.getNode(path).getInt();
    }

    /**
     * Grabs an int from the specified node
     *
     * @param path
     * @return the int from the node, null references will zero initialize
     */
    public int getIntValue(String... path) {
        return userRootNode.getNode(path).getInt();
    }

    /**
     * Grabs a double from the specified node
     *
     * @param path
     * @return the double from the node, null references will zero initialize
     */
    public double getDoubleValue(String... path) {
        return userRootNode.getNode(path).getDouble();
    }

    /**
     * Grabs a long from the specified node
     *
     * @param path
     * @return the long from the node, null references will zero initialize
     */
    public long getLongValue(String... path) {
        return userRootNode.getNode(path).getLong();
    }

    /**
     * Grabs a boolean from the specified node
     *
     * @param path
     * @return the boolean from the node, null references will zero initialize
     */
    public boolean getBooleanValue(String... path) {
        return userRootNode.getNode(path).getBoolean();
    }

    /**
     * Grabs a string from the specified node
     *
     * @param path
     * @return the string from the node, null references will zero initialize
     */
    public String getStringValue(String... path) {
        return userRootNode.getNode(path).getString();
    }

    /**
     * Checks to see if a node exists in the user's config file
     *
     * @param path path to the node
     * @return true if the node exists
     */
    public boolean hasNode(String... path) {
        return (userRootNode.getNode(path) != null);
    }

    /**
     * Returns the children of a specific node
     *
     * @param path the path to the parent node
     * @return the list of children for the target parent node
     */
    public List<? extends CommentedConfigurationNode> getChildren(String... path) {
        return userRootNode.getNode(path).getChildrenList();
    }

    public List<String> getListFromNode(String... path) throws ObjectMappingException {
        return userRootNode.getNode(path).getList(TypeToken.of(String.class));
    }
}

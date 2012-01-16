package de.haw.hamburg.inf.pro.ws11.petersenwienrich;

public class Config {
    /**
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * TODO: EDIT THIS TO SET CORRECT LOCATIONS WHEN FILES NOT FOUND
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */

    private static final String PROJECT_HOME = "C:\\Users\\Umbriel\\workspace\\Phoenix3D\\";
    // private static final String PROJECT_HOME =
    // "/home/elresidente/Develop/Repository/interactive-installations-phoenix3d/";

    /* ########################################################### */

    public static final String IMAGES_LOCATION = PROJECT_HOME
            + "resources\\images\\";
    // public static final String IMAGES_LOCATION = PROJECT_HOME +
    // "resources/images/";

    /* ########################################################### */

    public static final String CL_LOCATION = PROJECT_HOME + "resources\\cl\\";
    // public static final String CL_LOCATION = PROJECT_HOME + "resources/cl/";

    // ParticleConstants
    public final static int NUM_PARTICLES = 1024 * 512;

    public static final int POINTSIZE = 3;

    public static final int TIME_CHAOS = 400;

    public static final int TIME_SWARMING = 1000;

    public static final int TIME_BEING = 150;

    public static final int TIME_EXPANDING = 60;

    public static final int TIME_DESINTEGRATING = 800;

    public static final int TIME_FLOOR = 600;

}

package Identitaet;

public class Config {
    /**
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! TODO: EDIT THIS TO SET CORRECT LOCATIONS
     * WHEN FILES NOT FOUND !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */

    // private static final String PROJECT_HOME = "C:\\Users\\Umbriel\\workspace\\Phoenix3D\\";

    private static final String PROJECT_HOME = "C:\\Users\\Tollwood\\git\\Identitaet\\";


//    private static final String PROJECT_HOME = "/home/elresidente/Develop/Repository/Identitaet/";

    /* ########################################################### */

    public static final String IMAGES_LOCATION = PROJECT_HOME + "resources\\images\\";
    // public static final String IMAGES_LOCATION = PROJECT_HOME +
    // "resources/images/";

    /* ########################################################### */

    public static final String CL_LOCATION = PROJECT_HOME + "resources\\cl\\";
    // public static final String CL_LOCATION = PROJECT_HOME + "resources/cl/";

    /* ########################################################### */
    // Color Mode Constants

    public static final int COLOR_FULL = 200;
    public static final int COLOR_WHITE_ON_BLACK = 201;
    public static final int COLOR_WHITE_ON_WHITE = 202;
    public static final int COLOR_WHITE_ON_GREY = 203;
    public static final int COLOR_DUST = 204;

    public static final int COLOR_MODE = COLOR_FULL;

    /* ########################################################### */
    // Room Mode Constants
    public static final int ROOM_SHABY = 300;
    public static final int ROOM_WHITE = 301;

    public static final int ROOM_MODE = ROOM_WHITE;

    /* ########################################################### */
    // ParticleConstants

    public final static int NUM_PARTICLES = 1024 * 394;

    public static final int POINTSIZE = 3;

    public static final int TIME_CHAOS = 125;

    public static final int TIME_SWARMING = 600;

    public static final int TIME_BEING = 150;

    public static final int TIME_EXPANDING = 60;

    public static final int TIME_DESINTEGRATING = 800;

    public static final int TIME_FLOOR = 400;

}

package Identitaet;
//mail@laurastolte.de -> screenshot 300dpi

import static javax.media.opengl.GL.GL_ARRAY_BUFFER_ARB;
import static javax.media.opengl.GL.GL_DYNAMIC_COPY_ARB;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.media.opengl.GL;

import msa.opencl.OpenCL;
import msa.opencl.OpenCLBuffer;
import msa.opencl.OpenCLKernel;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.opengl.PGraphicsOpenGL;
import SimpleOpenNI.SimpleOpenNI;

import com.nativelibs4java.opencl.CLMem;

public class Identitaet extends PApplet {

    private static final long serialVersionUID = 1L;

    // Particle Size Constants
    private final int PARTICLE_NFLOAT = 8;
    private final int PARTICLEFLOATSIZE = Config.NUM_PARTICLES * PARTICLE_NFLOAT;
    private final int PARTICLEBYTESIZE = PARTICLEFLOATSIZE * 4;

    // Modes
    private static final int MODE_STATIC = 0;
    private static final int MODE_GRAVITY = 1;
    private static final int MODE_WEIGHTLESS = 2;
    private static final int MODE_NEG_WEIGHTLESS = 3;
    private static final int MODE_RANDOM = 4;
    private static final int MODE_PLANETARY = 5;
    private static final int MODE_PLANETARY_BUILD = 6;
    private static final int NMODES = 7;

    private int mayorMode = MODE_GRAVITY;

    // Gravity modes
    private static final int GRAVITY_FALLING = 102;
    private static final int GRAVITY_EXPLODE = 103;
    private int gravityMode = GRAVITY_FALLING;

    // Story phases
    private static final int PHASE_0_START = 1001;
    private static final int PHASE_1_CHAOS = 1002;
    private static final int PHASE_2_SWARMING = 1003;
    private static final int PHASE_3_BUILDING = 1004;
    private static final int PHASE_4_BEING = 1005;
    private static final int PHASE_5_DESINTEGRATING = 1006;
    private static final int PHASE_6_DIEING = 1007;

    private int phase = PHASE_0_START;

    // Ressources Location
    private static final String CL_RESSOURCE = Config.CL_LOCATION;
    private static final String IMG_RESSOURCE = Config.IMAGES_LOCATION;

    // Open*L Objects
    private OpenCL openCL;
    private PGraphicsOpenGL pgl;
    private GL gl;
    private OpenCLKernel clKernel;

    // FrameCounter
    private final FrequencyCounter fpsCounter = new FrequencyCounter();

    // Particle Buffers
    private Particle3D[] particle;
    private ByteBuffer particleBytes;
    private FloatBuffer particleBuffer;
    private OpenCLBuffer clMemParticles;
    private int[] VBOParticleBuffer;

    // ColorBuffers
    private int[] particleColor;
    private ByteBuffer colorBytes;
    private FloatBuffer colorBuffer;
    private OpenCLBuffer clMemColors;
    private int[] VBOColorBuffer;

    // Center of Mass Buffers
    private PVector[] userCenters = null;
    private ByteBuffer comBytes;
    private FloatBuffer comBuffer;
    private OpenCLBuffer clMemCom;
    private static final int MAX_USERS = 10;

    // SimpleOpenNI Objects
    private SimpleOpenNI ni;
    private float camZoomF = 0.24f;
    private float camRotX = radians(180) - 0.0f; // by default rotate the whole
                                                 // scene 180deg around the x-axis,
                                                 // the data from openni comes upside down
    private float camRotY = radians(0);

    // Moved Particle Counter
    private int tmpParticleNumber;

    // draw counter
    private int drawCount;

    // Room Textures
    private PImage rgbImg;
    private PImage backgroundImg;
    private PImage floorImg;
    private PImage lWallImg;
    private PImage rWallImg;
    private PImage ceilImg;

    // stopwatch
    long timestamp;

    // Room wall positions
    private float floorLevel;
    private float leftWall;
    private float rightWall;
    private float backWall;
    private float ceiling;

    // counter for moving backwards
    private int moveBackCounter;

    // texture IDs
    private final int[] texture = new int[5];

    // gravity multiplikator
    private float forceFaktor = 3.0f;

    // indicates that a user is in the cams view
    private boolean userInRoom;

    // step width for iterating over points
    private int pointCloudSteps = 2;

    // repeat count variable
    private int howOften = 0;

    // height of user for building
    private float heightToDraw;

    // Debug Text for on screen displaying
    String debugText = "";

    // maximum height of user
    private float maxUserHeight;

    // main function to let applet run fullscreen
    static public void main(String args[]) {
        PApplet.main(new String[] { "--present", "--bgcolor=#000000", "--present-stop-color=#000000",
                "Identitaet.Identitaet" });
    }

    @Override
    public void setup() {
        // SimpleOpenNI Setup
        timestamp = millis();
        ni = new SimpleOpenNI(this, SimpleOpenNI.RUN_MODE_MULTI_THREADED);
        ni.setMirror(true);
        ni.enableDepth();
        ni.enableUser(SimpleOpenNI.SKEL_PROFILE_ALL);
        ni.enableRGB();
        ni.alternativeViewPointDepthToImage();
        timestamp = millis() - timestamp;
        System.out.println("SimpleOpenNI Setup time: " + timestamp + " ms");

        // initialize Processing window:
        timestamp = millis();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        size(screen.width, screen.height, OPENGL);
        frameRate(25);
        timestamp = millis() - timestamp;
        System.out.println("Processing Setup time: " + timestamp + " ms");

        // set room dimensions
        floorLevel = -(height);
        ceiling = height + 200;
        leftWall = -3000;
        rightWall = 3000;
        backWall = 6000;
        heightToDraw = floorLevel + 0.5f;
        maxUserHeight = floorLevel + 0.5f;

        // load texture images
        if (Config.ROOM_MODE == Config.ROOM_SHABY) {
            backgroundImg = loadImage(IMG_RESSOURCE + "room1_backwall.jpg");
            floorImg = loadImage(IMG_RESSOURCE + "room1_floor.jpg");
            lWallImg = loadImage(IMG_RESSOURCE + "room1_left_wall.jpg");
            rWallImg = loadImage(IMG_RESSOURCE + "room1_right_wall.jpg");
            ceilImg = loadImage(IMG_RESSOURCE + "room1_ceiling.jpg");
        } else if (Config.ROOM_MODE == Config.ROOM_WHITE) {
            backgroundImg = loadImage(IMG_RESSOURCE + "white_back.jpg");
            floorImg = loadImage(IMG_RESSOURCE + "white_floor.jpg");
            lWallImg = loadImage(IMG_RESSOURCE + "white_left.jpg");
            rWallImg = loadImage(IMG_RESSOURCE + "white_right.jpg");
            ceilImg = loadImage(IMG_RESSOURCE + "white_ceiling.jpg");
        }

        // initialize GL object
        timestamp = millis();
        pgl = (PGraphicsOpenGL) g;
        gl = pgl.beginGL();

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);

        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE);

        // generate openGL Textures
        gl.glGenTextures(5, texture, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[0]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, backgroundImg.width, backgroundImg.height, 0, GL.GL_BGRA,
                GL.GL_UNSIGNED_BYTE, IntBuffer.wrap(backgroundImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        // #####
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[1]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, floorImg.width, floorImg.height, 0, GL.GL_BGRA,
                GL.GL_UNSIGNED_BYTE, IntBuffer.wrap(floorImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        // #####
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[2]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, lWallImg.width, lWallImg.height, 0, GL.GL_BGRA,
                GL.GL_UNSIGNED_BYTE, IntBuffer.wrap(lWallImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        // #####
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[3]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, rWallImg.width, rWallImg.height, 0, GL.GL_BGRA,
                GL.GL_UNSIGNED_BYTE, IntBuffer.wrap(rWallImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        // #####
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[4]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, ceilImg.width, ceilImg.height, 0, GL.GL_BGRA,
                GL.GL_UNSIGNED_BYTE, IntBuffer.wrap(ceilImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

        timestamp = millis() - timestamp;
        System.out.println("OpenGL Setup time: " + timestamp + " ms");

        // init openCL
        timestamp = millis();
        openCL = OpenCL.getSingleton();
        openCL.setupFromOpenGL();
        timestamp = millis() - timestamp;
        System.out.println("OpenCL Setup time: " + timestamp + " ms");

        System.out.println("Interoperability: " + openCL.isSharingContext());

        tmpParticleNumber = 0;

        // init particle buffer
        timestamp = millis();
        particle = new Particle3D[Config.NUM_PARTICLES];
        particleBytes = openCL.createByteBuffer(PARTICLEBYTESIZE);
        particleBuffer = particleBytes.asFloatBuffer();

        // init color buffer
        particleColor = new int[Config.NUM_PARTICLES];
        colorBytes = openCL.createByteBuffer(Config.NUM_PARTICLES * 4 * OpenCL.SIZEOF_FLOAT);
        colorBuffer = colorBytes.asFloatBuffer();

        // init CoM Buffer
        comBytes = openCL.createByteBuffer(MAX_USERS * 4 * OpenCL.SIZEOF_FLOAT);
        comBuffer = comBytes.asFloatBuffer();

        timestamp = millis() - timestamp;
        System.out.println("OpenCL Buffer Setup time: " + timestamp + " ms");

        timestamp = millis();

        int initialParticleColor = 0;
        // set initial particle color
        switch (Config.COLOR_MODE) {
        case Config.COLOR_FULL:
            initialParticleColor = 0;
            break;
        case Config.COLOR_WHITE_ON_BLACK:
            initialParticleColor = 255;
            break;
        case Config.COLOR_WHITE_ON_GREY:
            initialParticleColor = 255;
            break;
        case Config.COLOR_WHITE_ON_WHITE:
            initialParticleColor = 255;
            break;
        case Config.COLOR_DUST:
            initialParticleColor = 128;
            break;
        default:
            initialParticleColor = 255;
            break;
        }

        // generate particles
        for (int i = 0; i < Config.NUM_PARTICLES; i++) {
            particle[i] = new Particle3D(0, floorLevel, backWall / 2, 0, 0, 0);
            particleBuffer.put(particle[i].x);
            particleBuffer.put(particle[i].y);
            particleBuffer.put(particle[i].z);
            particleBuffer.put(particle[i].velX);
            particleBuffer.put(particle[i].velY);
            particleBuffer.put(particle[i].velZ);
            particleColor[i] = color(initialParticleColor);
            colorBuffer.put(red(particleColor[i]));
            colorBuffer.put(green(particleColor[i]));
            colorBuffer.put(blue(particleColor[i]));
            colorBuffer.put(alpha(particleColor[i]));
            // colorBuffer.put(0.0f);
        }

        particleBuffer.rewind();
        colorBuffer.rewind();
        timestamp = millis() - timestamp;
        System.out.println("OpenCL Buffer Fill time: " + timestamp + " ms");

        timestamp = millis();

        // prepare virtual buffer objects
        VBOParticleBuffer = new int[1];
        // This does nothing more than create a handle (like a link) to a
        // buffer.
        gl.glGenBuffersARB(1, VBOParticleBuffer, 0);
        // This attaches our handle to the array buffer. In essence, an array
        // buffer is a buffer used to store the
        // vertex Positions and Speeds for OpenCL.
        gl.glBindBufferARB(GL_ARRAY_BUFFER_ARB, VBOParticleBuffer[0]);
        // This allocates the memory needed to store our point data, and also
        // fills it with our point data (variable:
        // particleBuffer);
        gl.glBufferDataARB(GL_ARRAY_BUFFER_ARB, PARTICLEBYTESIZE, particleBuffer, GL_DYNAMIC_COPY_ARB);
        // detach handle from Buffer
        gl.glBindBufferARB(GL_ARRAY_BUFFER_ARB, 0);

        VBOColorBuffer = new int[1];
        gl.glGenBuffersARB(1, VBOColorBuffer, 0);
        gl.glBindBufferARB(GL_ARRAY_BUFFER_ARB, VBOColorBuffer[0]);
        gl.glBufferDataARB(GL_ARRAY_BUFFER_ARB, PARTICLEBYTESIZE, colorBuffer, GL_DYNAMIC_COPY_ARB);
        gl.glBindBufferARB(GL_ARRAY_BUFFER_ARB, 0);
        timestamp = millis() - timestamp;
        System.out.println("OpenCL VBO Setup time: " + timestamp + " ms");

        // create CL Objects from openGL VBOs
        clMemParticles = openCL.createBufferFromGLObject(VBOParticleBuffer[0], CLMem.Usage.InputOutput);
        clMemColors = openCL.createBufferFromGLObject(VBOColorBuffer[0], CLMem.Usage.InputOutput);

        clMemCom = new OpenCLBuffer();
        clMemCom.initBuffer(MAX_USERS * 4 * OpenCL.SIZEOF_FLOAT, CLMem.Usage.InputOutput, comBuffer);

        // load openCL program
        System.out.println("Load particle program");
        openCL.loadProgramFromFile(dataPath(CL_RESSOURCE + "Particle3D.cl"));
        clKernel = openCL.loadKernel("updateParticle");

        // set arguments
        clKernel.setArg(0, clMemParticles.getCLMem());
        clKernel.setArg(1, clMemColors.getCLMem());
        clKernel.setArg(2, clMemCom.getCLMem());
        clKernel.setArg(3, floorLevel);
        clKernel.setArg(4, ceiling);
        clKernel.setArg(5, leftWall);
        clKernel.setArg(6, rightWall);
        clKernel.setArg(7, backWall);
        clKernel.setArg(8, mayorMode);
        clKernel.setArg(9, forceFaktor);

        gl.glPointSize(Config.POINTSIZE);
        pgl.endGL();
        perspective(95, width / height, 10, 150000);

    }

    /** story progress engine set new phase and mode depending of a set of variables */
    private void progressStory() {
        if (!userInRoom) {
            phase = PHASE_0_START;
            mayorMode = MODE_GRAVITY;
            gravityMode = GRAVITY_FALLING;
            howOften = 0;
            moveBackCounter = 0;
            drawCount = 0;
        } else {
            if (phase == PHASE_0_START) {
                debugText = "Phase 0";
                if (userInRoom) {
                    phase = PHASE_1_CHAOS;
                    mayorMode = MODE_RANDOM;
                    drawCount = 0;
                }
            } else if (phase == PHASE_1_CHAOS) {
                debugText = "Phase 1";
                if (moveBackCounter >= Config.TIME_CHAOS) {
                    mayorMode = MODE_PLANETARY;
                    phase = PHASE_2_SWARMING;
                    moveBackCounter = 0;
                    drawCount = 0;
                } else {
                    moveBackCounter++;
                }

            } else if (phase == PHASE_2_SWARMING) {
                debugText = "Phase 2";
                if (moveBackCounter >= Config.TIME_SWARMING) {
                    mayorMode = MODE_PLANETARY_BUILD;
                    phase = PHASE_3_BUILDING;
                    pointCloudSteps = 2;
                    moveBackCounter = 0;
                    drawCount = 1;
                    heightToDraw = floorLevel + 0.5f;
                    maxUserHeight = floorLevel + 20.5f;
                } else {
                    moveBackCounter++;
                }
            } else if (phase == PHASE_3_BUILDING) {
                debugText = "Phase 3";
                if (heightToDraw <= ceiling / 2) {
                    heightToDraw += 10.0f;
                    forceFaktor++;

                } else {
                    System.out.println("Max:" + maxUserHeight + " Act: " + heightToDraw);
                    phase = PHASE_4_BEING;
                    mayorMode = MODE_STATIC;
                    pointCloudSteps = 2;
                    drawCount = 0;
                    forceFaktor = 3.0f;
                    heightToDraw = ceiling;
                }
            } else if (phase == PHASE_4_BEING) {
                debugText = "Phase 4";
                if (moveBackCounter >= Config.TIME_BEING) {
                    phase = PHASE_5_DESINTEGRATING;
                    mayorMode = MODE_WEIGHTLESS;
                    moveBackCounter = 0;
                    drawCount = 0;
                    howOften = 1;
                } else {
                    moveBackCounter++;
                }
            } else if (phase == PHASE_5_DESINTEGRATING) {
                debugText = "Phase 5";
                if (mayorMode == MODE_WEIGHTLESS && moveBackCounter >= Config.TIME_EXPANDING) {
                    drawCount = 1;
                    mayorMode = MODE_GRAVITY;
                    gravityMode = GRAVITY_FALLING;
                    howOften = 1000;
                    moveBackCounter = 0;
                    pointCloudSteps = 1000;
                } else if (mayorMode == MODE_GRAVITY && moveBackCounter < Config.TIME_DESINTEGRATING) {
                    if (pointCloudSteps > 500) {
                        pointCloudSteps -= 4;
                    } else if (pointCloudSteps > 200) {
                        pointCloudSteps -= 3;
                    } else if (pointCloudSteps > 100) {
                        pointCloudSteps -= 2;
                    } else if (pointCloudSteps > 2) {
                        pointCloudSteps--;
                    } else {
                        moveBackCounter = Config.TIME_DESINTEGRATING;
                        howOften = 1;
                    }
                    moveBackCounter++;
                } else if (mayorMode == MODE_GRAVITY && moveBackCounter >= Config.TIME_DESINTEGRATING) {
                    moveBackCounter = 0;
                    mayorMode = MODE_GRAVITY;
                    pointCloudSteps = 2;
                    phase = PHASE_6_DIEING;
                    drawCount = 0;
                } else {
                    moveBackCounter++;
                }
            } else if (phase == PHASE_6_DIEING) {
                debugText = "Phase 6";
                if (!userInRoom || moveBackCounter >= Config.TIME_FLOOR) {
                    phase = PHASE_0_START;
                    moveBackCounter = 0;
                    howOften = 0;
                    drawCount = 0;
                } else {
                    moveBackCounter++;
                }
            }
        }
    }

    public void draw() {
        // get GL context
        PGraphicsOpenGL pgl = (PGraphicsOpenGL) g;
        // Update SimpleOpenNi
        ni.update();

        // set background
        switch (Config.COLOR_MODE) {
        case Config.COLOR_FULL:
            background(0);
            break;
        case Config.COLOR_WHITE_ON_BLACK:
            background(0);
            break;
        case Config.COLOR_WHITE_ON_GREY:
            background(128);
            break;
        case Config.COLOR_WHITE_ON_WHITE:
            background(230);
            break;
        case Config.COLOR_DUST:
            background(0);
            break;
        default:
            background(0);
            break;
        }

        // FPS Counter update
        fpsCounter.triggerCount();

        // set the cam position
        translate(width / 2, height / 2, 100.0f);
        rotateX(camRotX);
        rotateY(camRotY);
        scale(camZoomF);

        // get new rgb image
        rgbImg = ni.rgbImage();

        // get user count
        int userCount = ni.getNumberOfUsers();
        if (userCount > MAX_USERS) {
            userCount = MAX_USERS;
        }

        // delete old user map
        int[] userMap = null;

        if (userCount > 0) {
            // first assume there's no one beside of userCount
            userInRoom = false;
            userMap = ni.getUsersPixels(SimpleOpenNI.USERS_ALL);
            userCenters = new PVector[MAX_USERS];
            // calculate mass centers
            for (int i = 0; i < MAX_USERS; i++) {
                userCenters[i] = new PVector();
                ni.getCoM(i + 1, userCenters[i]);
                if (userCenters[i].x != 0.0 && userCenters[i].y != 0.0 && userCenters[i].z != 0.0) {
                    // when we get a CoM different from 0,0,0 then there truely
                    // is somebody
                    userInRoom = true;
                }
            }

            progressStory();
            // draw depending on actual mode
            if (mayorMode == MODE_STATIC) {
                if (Config.COLOR_MODE == Config.COLOR_FULL || Config.COLOR_MODE == Config.COLOR_DUST) {
                    drawBackground();
                }
                drawStaticPoints(pgl, pointCloudSteps, userMap);
            } else if (mayorMode == MODE_GRAVITY) {
                if (Config.COLOR_MODE == Config.COLOR_FULL || Config.COLOR_MODE == Config.COLOR_DUST) {
                    drawBackground();
                }
                if (phase == PHASE_5_DESINTEGRATING) {
                    drawStaticPoints(pgl, 2, userMap);
                }
                makeParticlesFreeMoving(pointCloudSteps, userMap, howOften);
            } else if (mayorMode == MODE_WEIGHTLESS || mayorMode == MODE_NEG_WEIGHTLESS) {
                makeParticlesFreeMoving(pointCloudSteps, userMap, howOften);
                if (Config.COLOR_MODE == Config.COLOR_FULL || Config.COLOR_MODE == Config.COLOR_DUST) {
                    drawBackground();
                }
            } else if (mayorMode == MODE_RANDOM) {
                makeAllParticlesRandom();
                if (Config.COLOR_MODE == Config.COLOR_FULL || Config.COLOR_MODE == Config.COLOR_DUST) {
                    drawBackground();
                }
            } else if (mayorMode == MODE_PLANETARY || mayorMode == MODE_PLANETARY_BUILD) {
                if (Config.COLOR_MODE == Config.COLOR_FULL || Config.COLOR_MODE == Config.COLOR_DUST) {
                    drawBackground();
                }
                if (phase == PHASE_3_BUILDING) {
                    drawStaticPoints(pgl, pointCloudSteps, userMap);
                }
            }
        } else {
            userInRoom = false;
            phase = PHASE_0_START;
            mayorMode = MODE_GRAVITY;
            gravityMode = GRAVITY_FALLING;
            if (Config.COLOR_MODE == Config.COLOR_FULL || Config.COLOR_MODE == Config.COLOR_DUST) {
                drawBackground();
            }

        }

        updateCenterOfMassVBO(userCenters);

        Update();
        Render();

        // ///Debug Stuff
        // drawCenterOfMass(userCenters);
        // PFont metaBold = createFont("FFScala", 88);
        //
        // textFont(metaBold, 88);
        // stroke(255);
        // fill(0);
        // text(debugText + "\nUsers: " + userCount, 0, 0);
        // System.out.println("FPS:" + fpsCounter.getFrequency());
    }

    private void updateCenterOfMassVBO(PVector[] centers) {
        if (centers != null) {
            for (int i = 0; i < centers.length; i++) {
                comBuffer.put(i * 4 + 0, centers[i].x);
                if (phase == PHASE_3_BUILDING) {
                    comBuffer.put(i * 4 + 1, heightToDraw);

                } else {
                    comBuffer.put(i * 4 + 1, centers[i].y);
                }
                comBuffer.put(i * 4 + 2, centers[i].z);
                comBuffer.put(i * 4 + 3, 0);
            }
            for (int i = centers.length; i < MAX_USERS; i++) {
                comBuffer.put(i * 4 + 0, 0);
                comBuffer.put(i * 4 + 1, 0);
                comBuffer.put(i * 4 + 2, 0);
                comBuffer.put(i * 4 + 3, 0);
            }
        } else {
            for (int i = 0; i < MAX_USERS; i++) {
                comBuffer.put(i * 4 + 0, 0);
                comBuffer.put(i * 4 + 1, 0);
                comBuffer.put(i * 4 + 2, 0);
                comBuffer.put(i * 4 + 3, 0);
            }
        }
    }

    /** draw mass centers */
    private void drawCenterOfMass(PVector[] centers) {
        if (centers != null) {
            gl = pgl.beginGL();
            gl.glPointSize(8);
            gl.glBegin(GL.GL_POINTS);
            for (int i = 0; i < centers.length; i++) {
                gl.glColor3f(0, 255, 0);
                gl.glVertex3f(centers[i].x, centers[i].y, centers[i].z);
                // System.out.println("X:" + centers[i].x + " Y:" + centers[i].y
                // + " Z:" + centers[i].z);
            }
            gl.glColor3f(255, 0, 0);
            gl.glVertex3f(0.0f, (float) heightToDraw, (float) backWall / 2);
            gl.glEnd();
            gl.glPointSize(Config.POINTSIZE);
        }
    }

    /** gives every particle a random spherical direction */
    private void makeAllParticlesRandom() {
        if (drawCount < 1) {
            for (int i = 0; i < Config.NUM_PARTICLES; i++) {
                if (tmpParticleNumber >= Config.NUM_PARTICLES) {
                    tmpParticleNumber = 0;
                }
                updateVBOtoParticle(tmpParticleNumber);
                particle[tmpParticleNumber].y = floorLevel;
                float a = random(80, 100);
                float b = random(0, 360);
                particle[tmpParticleNumber].velX = 20 * sin(a) * cos(b);
                particle[tmpParticleNumber].velY = 20 * cos(a);
                particle[tmpParticleNumber].velZ = 20 * sin(a) * sin(b);
                updateParticleToVBO(tmpParticleNumber);
                tmpParticleNumber++;
            }
            drawCount++;
        }
    }

    /** puts all particles to ground with no movement */
    private void putAllParticlesToGround() {
        if (drawCount <= 0) {
            for (int i = 0; i < Config.NUM_PARTICLES; i++) {
                if (tmpParticleNumber >= Config.NUM_PARTICLES) {
                    tmpParticleNumber = 0;
                }
                particle[tmpParticleNumber].y = floorLevel;
                particle[i].y = floorLevel;
                particle[tmpParticleNumber].velX = 0;
                particle[tmpParticleNumber].velY = 0;
                particle[tmpParticleNumber].velZ = 0;
                paintParticle(color(0, 0, 0, 0), tmpParticleNumber);
                updateParticleToVBO(tmpParticleNumber);
                tmpParticleNumber++;
            }
        }
    }

    /** gives every particle a random direction */
    private void makeParticlesFreeMoving(int pointCloudSteps, int[] userMap, int howOften) {
        PVector realWorldPoint;
        if (userMap != null) {
            if (drawCount < howOften) {
                for (int i = 0; i < userMap.length; i += pointCloudSteps) {
                    realWorldPoint = ni.depthMapRealWorld()[i];
                    int x = i % ni.depthWidth();
                    int y = (i - x) / ni.depthWidth();
                    // check if there is a user
                    if (userMap[i] != 0) {
                        setGravityParticle(realWorldPoint.x + random(-2, 2), realWorldPoint.y + random(-2, 2),
                                realWorldPoint.z + random(-2, 2), x, y);
                    }
                }
            }
            drawCount++;
        }
    }

    /** gives new values to next particle */
    public void setGravityParticle(float x, float y, float z, int realX, int realY) {
        if (tmpParticleNumber >= Config.NUM_PARTICLES) {
            tmpParticleNumber = 0;
        }
        setGravityParticle(x, y, z, realX, realY, tmpParticleNumber);
        tmpParticleNumber++;
    }

    /** gives new values to particle particleNumber */
    public void setGravityParticle(float x, float y, float z, int realX, int realY, int particleNumber) {
        if (z <= 10) {
            return;
        }

        particle[particleNumber].x = x + random(-1.0f, 1.0f);
        particle[particleNumber].y = y + random(-1.0f, 1.0f);
        particle[particleNumber].z = z + random(-1.0f, 1.0f);

        if (gravityMode == GRAVITY_FALLING) {
            // falling mode
            float a = random(0, PI);
            float b = random(0, TWO_PI);
            particle[particleNumber].velX = sin(a) * cos(b);
            particle[particleNumber].velY = cos(a);
            particle[particleNumber].velZ = sin(a) * sin(b);
        } else if (gravityMode == GRAVITY_EXPLODE) {
            // explode mode
            float a = random(0, PI);
            float b = random(0, TWO_PI);
            particle[particleNumber].velX = 50 * sin(a) * cos(b);
            particle[particleNumber].velY = 50 * cos(a);
            particle[particleNumber].velZ = 50 * sin(a) * sin(b);
        }
        updateParticleToVBO(particleNumber);
        paintParticle(realX, realY, particleNumber);
    }

    /** directly draw user mask points */
    private void drawStaticPoints(PGraphicsOpenGL pgl, int pointCloudSteps, int[] userMap) {
        PVector realWorldPoint;

        if (userMap != null) {
            gl = pgl.beginGL();
            gl.glEnable(GL.GL_POINTS);
            gl.glBegin(GL.GL_POINTS);
            for (int i = 0; i < userMap.length; i += pointCloudSteps) {
                realWorldPoint = ni.depthMapRealWorld()[i];
                int x = i % ni.depthWidth();
                int y = (i - x) / ni.depthWidth();
                // check if point is higher than heightToDraw
                if (realWorldPoint.y > heightToDraw) {
                    // System.out.println("Act:" + realWorldPoint.y + " ToDraw:"
                    // + heightToDraw);
                    continue;
                }
                // check if there is a user
                if (userMap[i] != 0) {
                    switch (Config.COLOR_MODE) {
                    case Config.COLOR_FULL:
                        gl.glColor3f(norm(red(rgbImg.get(x, y)), 0, 255), norm(green(rgbImg.get(x, y)), 0, 255),
                                norm(blue(rgbImg.get(x, y)), 0, 255));
                        break;
                    case Config.COLOR_WHITE_ON_BLACK:
                        gl.glColor3f(1.0f, 1.0f, 1.0f);
                        break;
                    case Config.COLOR_WHITE_ON_GREY:
                        gl.glColor3f(1.0f, 1.0f, 1.0f);
                        break;
                    case Config.COLOR_WHITE_ON_WHITE:
                        gl.glColor3f(1.0f, 1.0f, 1.0f);
                        break;
                    case Config.COLOR_DUST:
                        gl.glColor3f(0.5f, 0.5f, 0.5f);
                        break;
                    default:
                        gl.glColor3f(norm(red(rgbImg.get(x, y)), 0, 255), norm(green(rgbImg.get(x, y)), 0, 255),
                                norm(blue(rgbImg.get(x, y)), 0, 255));
                        break;

                    }
                    gl.glColor4f(norm(red(rgbImg.get(x, y)), 0, 255), norm(green(rgbImg.get(x, y)), 0, 255),
                            norm(blue(rgbImg.get(x, y)), 0, 255), random(0.0f, 1.0f));
                    gl.glVertex3f(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
            }

            gl.glEnd();
            gl.glDisable(GL.GL_POINTS);
            pgl.endGL();
        }
    }

    /** give rgbimage color to specific particle */
    private void paintParticle(int realX, int realY, int particleNumber) {
        switch (Config.COLOR_MODE) {
        case Config.COLOR_FULL:
            paintParticle(rgbImg.get(realX, realY), particleNumber);
            break;
        case Config.COLOR_WHITE_ON_BLACK:
            paintParticle(255, particleNumber);
            break;
        case Config.COLOR_WHITE_ON_GREY:
            paintParticle(255, particleNumber);
            break;
        case Config.COLOR_WHITE_ON_WHITE:
            paintParticle(255, particleNumber);
            break;
        case Config.COLOR_DUST:
            paintParticle(128, particleNumber);
            break;
        default:
            paintParticle(rgbImg.get(realX, realY), particleNumber);
            break;
        }

    }

    /** give specific color to specific particle */
    private void paintParticle(int color, int particleNumber) {
        particleColor[particleNumber] = color;

        colorBuffer.put(particleNumber * 4 + 0, norm(red(particleColor[particleNumber]), 0, 255));
        colorBuffer.put(particleNumber * 4 + 1, norm(green(particleColor[particleNumber]), 0, 255));
        colorBuffer.put(particleNumber * 4 + 2, norm(blue(particleColor[particleNumber]), 0, 255));
        colorBuffer.put(particleNumber * 4 + 3, norm(alpha(particleColor[particleNumber]), 0, 255));
    }

    public void drawBackground() {
        background(0);
        gl = pgl.beginGL();
        {
            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, texture[0]);
            gl.glColor3f(255, 255, 255);

            gl.glBegin(GL.GL_QUADS);
            {
                gl.glTexCoord2f(0f, 0f);
                gl.glVertex3f(leftWall, ceiling, backWall);
                gl.glTexCoord2f(1f, 0f);
                gl.glVertex3f(rightWall, ceiling, backWall);
                gl.glTexCoord2f(1f, 1f);
                gl.glVertex3f(rightWall, floorLevel, backWall);
                gl.glTexCoord2f(0f, 1f);
                gl.glVertex3f(leftWall, floorLevel, backWall);
            }
            gl.glEnd();
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, texture[1]);

            gl.glBegin(GL.GL_QUADS);
            {
                gl.glTexCoord2f(0f, 0f);
                gl.glVertex3f(leftWall, floorLevel, backWall);
                gl.glTexCoord2f(1f, 0f);
                gl.glVertex3f(rightWall, floorLevel, backWall);
                gl.glTexCoord2f(1f, 1f);
                gl.glVertex3f(rightWall, floorLevel, 0);
                gl.glTexCoord2f(0f, 1f);
                gl.glVertex3f(leftWall, floorLevel, 0);
                gl.glDisable(GL.GL_TEXTURE_2D);
            }
            gl.glEnd();
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

            gl.glEnable(GL.GL_TEXTURE_2D);
            // gl.glActiveTexture(GL.GL_TEXTURE1);
            gl.glBindTexture(GL.GL_TEXTURE_2D, texture[2]);

            gl.glBegin(GL.GL_QUADS);
            {
                gl.glTexCoord2f(0f, 0f);
                gl.glVertex3f(leftWall, ceiling, 0);
                gl.glTexCoord2f(1f, 0f);
                gl.glVertex3f(leftWall, ceiling, backWall);
                gl.glTexCoord2f(1f, 1f);
                gl.glVertex3f(leftWall, floorLevel, backWall);
                gl.glTexCoord2f(0f, 1f);
                gl.glVertex3f(leftWall, floorLevel, 0);
                gl.glDisable(GL.GL_TEXTURE_2D);
            }
            gl.glEnd();
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, texture[3]);

            gl.glBegin(GL.GL_QUADS);
            {
                gl.glTexCoord2f(0f, 0f);
                gl.glVertex3f(rightWall, ceiling, backWall);
                gl.glTexCoord2f(1f, 0f);
                gl.glVertex3f(rightWall, ceiling, 0);
                gl.glTexCoord2f(1f, 1f);
                gl.glVertex3f(rightWall, floorLevel, 0);
                gl.glTexCoord2f(0f, 1f);
                gl.glVertex3f(rightWall, floorLevel, backWall);
                gl.glDisable(GL.GL_TEXTURE_2D);
            }
            gl.glEnd();
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, texture[4]);

            gl.glBegin(GL.GL_QUADS);
            {
                gl.glTexCoord2f(0f, 0f);
                gl.glVertex3f(leftWall, ceiling, 0);
                gl.glTexCoord2f(1f, 0f);
                gl.glVertex3f(rightWall, ceiling, 0);
                gl.glTexCoord2f(1f, 1f);
                gl.glVertex3f(rightWall, ceiling, backWall);
                gl.glTexCoord2f(0f, 1f);
                gl.glVertex3f(leftWall, ceiling, backWall);
                gl.glDisable(GL.GL_TEXTURE_2D);
            }
            gl.glEnd();
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        }
        pgl.endGL();
    }

    public void drawRoom() {
        gl = pgl.beginGL();
        float c = 255;
        // line floor
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glColor3f(c, c, c);
            gl.glTexCoord2f(0, 0);
            gl.glVertex3f(leftWall, floorLevel + .5f, 0);
            gl.glTexCoord2f(1, 0);
            gl.glVertex3f(rightWall, floorLevel + .5f, 0);
            gl.glTexCoord2f(1, 1);
            gl.glVertex3f(rightWall, floorLevel + .5f, backWall);
            gl.glTexCoord2f(0, 1);
            gl.glVertex3f(leftWall, floorLevel + .5f, backWall);
        }
        gl.glEnd();

        // line room
        gl.glBegin(GL.GL_LINES);
        {
            gl.glColor3f(c, c, c);
            gl.glVertex3f(leftWall, floorLevel + .5f, 0);
            gl.glVertex3f(leftWall, 2000, 0);
            gl.glVertex3f(leftWall, floorLevel + .5f, backWall);
            gl.glVertex3f(leftWall, 2000, backWall);
            gl.glVertex3f(rightWall, floorLevel + .5f, 0);
            gl.glVertex3f(rightWall, 2000, 0);
            gl.glVertex3f(rightWall, floorLevel + .5f, backWall);
            gl.glVertex3f(rightWall, 2000, backWall);
        }
        gl.glEnd();

        pgl.endGL();
    }

    void Render() {
        gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, VBOColorBuffer[0]);
        gl.glEnableClientState(GL.GL_COLOR_ARRAY);
        gl.glColorPointer(4, GL.GL_FLOAT, 0, 0);

        gl = pgl.beginGL();
        {
            openCL.finish();

            gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, VBOParticleBuffer[0]);

            gl.glPushMatrix();
            gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
            gl.glVertexPointer(3, GL.GL_FLOAT, 32, 0);
            gl.glDrawArrays(GL.GL_POINTS, 0, Config.NUM_PARTICLES);
            gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, 0);
            gl.glDisableClientState(GL.GL_VERTEX_ARRAY);
            gl.glDisableClientState(GL.GL_COLOR_ARRAY);
            gl.glPopMatrix();
        }
        pgl.endGL();

    }

    /** Update openCL Objects */
    void Update() {

        openCL.acquireGLObjects();

        gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, VBOColorBuffer[0]);
        gl.glBufferDataARB(GL_ARRAY_BUFFER_ARB, PARTICLEBYTESIZE, colorBuffer, GL_DYNAMIC_COPY_ARB);
        gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, 0);

        clMemParticles.write(particleBytes, 0, PARTICLEBYTESIZE, true);
        clMemCom.write(comBytes, 0, MAX_USERS * 4 * OpenCL.SIZEOF_FLOAT, true);

        try {
            long wgs = 0;
            clKernel.setArg(0, clMemParticles.getCLMem());
            clKernel.setArg(1, clMemColors.getCLMem());
            clKernel.setArg(2, clMemCom.getCLMem());
            clKernel.setArg(3, floorLevel);
            clKernel.setArg(4, ceiling);
            clKernel.setArg(5, leftWall);
            clKernel.setArg(6, rightWall);
            clKernel.setArg(7, backWall);
            clKernel.setArg(8, mayorMode);

            clKernel.run1D(Config.NUM_PARTICLES, (int) wgs);
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        clMemParticles.read(particleBytes, 0, PARTICLEBYTESIZE, true);
        particleBuffer = particleBytes.asFloatBuffer();

        // clMemColors.read(colorBytes, 0, Config.NUM_PARTICLES * 4 * 4, true);
        // colorBuffer = colorBytes.asFloatBuffer();

        openCL.releaseGLObjects();
    }

    private void updateParticleToVBO(int particleNumber) {
        particleBuffer.put(particleNumber * PARTICLE_NFLOAT + 0, particle[particleNumber].x);
        particleBuffer.put(particleNumber * PARTICLE_NFLOAT + 1, particle[particleNumber].y);
        particleBuffer.put(particleNumber * PARTICLE_NFLOAT + 2, particle[particleNumber].z);
        particleBuffer.put(particleNumber * PARTICLE_NFLOAT + 3, particle[particleNumber].velX);
        particleBuffer.put(particleNumber * PARTICLE_NFLOAT + 4, particle[particleNumber].velY);
        particleBuffer.put(particleNumber * PARTICLE_NFLOAT + 5, particle[particleNumber].velZ);
    }

    private void updateVBOtoParticle(int particleNumber) {
        particle[particleNumber].x = particleBuffer.get(particleNumber * PARTICLE_NFLOAT + 0);
        particle[particleNumber].y = particleBuffer.get(particleNumber * PARTICLE_NFLOAT + 1);
        particle[particleNumber].z = particleBuffer.get(particleNumber * PARTICLE_NFLOAT + 2);
        particle[particleNumber].velX = particleBuffer.get(particleNumber * PARTICLE_NFLOAT + 3);
        particle[particleNumber].velY = particleBuffer.get(particleNumber * PARTICLE_NFLOAT + 4);
        particle[particleNumber].velZ = particleBuffer.get(particleNumber * PARTICLE_NFLOAT + 5);
    }

    // Keyboard events

    @Override
    public void mousePressed() {
        mayorMode = (mayorMode + 1) % NMODES;
        drawCount = 0;
        System.out.println("Mode: " + mayorMode);
    }

    @Override
    public void keyPressed() {
        switch (key) {
        case ' ':
            ni.setMirror(!ni.mirror());
            break;
        case 'e':
            gravityMode = GRAVITY_EXPLODE;
            break;
        case 'f':
            gravityMode = GRAVITY_FALLING;
            break;
        case '+':
            forceFaktor += 0.1f;
            break;
        case '-':
            if (forceFaktor > 1.0f) {
                forceFaktor -= 0.1f;
            }
        }

        switch (keyCode) {

        case LEFT:
            camRotY += 0.1f;
            break;
        case RIGHT:
            // zoom out
            camRotY -= 0.1f;
            break;
        case UP:
            if (keyEvent.isShiftDown())
                camZoomF += 0.01f;
            else
                camRotX += 0.1f;
            break;
        case DOWN:
            if (keyEvent.isShiftDown()) {
                camZoomF -= 0.01f;
                if (camZoomF < 0.01)
                    camZoomF = 0.01f;
            } else
                camRotX -= 0.1f;
            break;
        }
    }

    void onNewUser(int userId) {
        // do stuff
        println("user acquired: " + userId);
    }

    void onLostUser(int userId) {
        // do stuff
        println("user lost: " + userId);

    }
}

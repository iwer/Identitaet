package de.haw.hamburg.inf.pro.ws11.petersenwienrich;

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

public class Phoenix3D extends PApplet {

    private static final long serialVersionUID = 1L;

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

    private static final int GRAVITY_FALLING = 102;
    private static final int GRAVITY_EXPLODE = 103;
    private int gravityMode = GRAVITY_FALLING;

    // phasen
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
                                                 // scene 180deg around
    // the x-axis,
    // the data from openni comes upside down
    private float camRotY = radians(0);
    private float camRotTick;

    // Moved Particle Counter
    private int tmpParticleNumber;

    // draw counter
    private int drawCount;

    // for the user colors
    private PImage rgbImg;
    private PImage backgroundImg;
    private PImage floorImg;
    private PImage lWallImg;
    private PImage rWallImg;
    private PImage ceilImg;

    // stopwatch
    long timestamp;

    private float floorLevel;
    private float leftWall;
    private float rightWall;
    private float backWall;
    private float ceiling;

    private int moveBackCounter;

    private final int[] texture = new int[5];

    private float forceFaktor = 3.0f;

    private boolean userInRoom;

    private int pointCloudSteps = 2;
    private int howOften = 0;

    String debugText = "";

    static public void main(String args[]) {
        PApplet.main(new String[] { "--present", "--bgcolor=#000000", "--present-stop-color=#000000",
                "de.haw.hamburg.inf.pro.ws11.petersenwienrich.Phoenix3D" });
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
        frameRate(30);
        timestamp = millis() - timestamp;
        System.out.println("Processing Setup time: " + timestamp + " ms");

        floorLevel = -(height + 500);
        ceiling = height + 500;
        leftWall = -3000;
        rightWall = 3000;
        backWall = 6000;

        backgroundImg = loadImage(IMG_RESSOURCE + "room1_backwall.jpg");
        floorImg = loadImage(IMG_RESSOURCE + "room1_floor.jpg");
        lWallImg = loadImage(IMG_RESSOURCE + "room1_left_wall.jpg");
        rWallImg = loadImage(IMG_RESSOURCE + "room1_right_wall.jpg");
        ceilImg = loadImage(IMG_RESSOURCE + "room1_ceiling.jpg");

        // initialize GL object
        timestamp = millis();
        pgl = (PGraphicsOpenGL) g;
        gl = pgl.beginGL();

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);

        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE);

        gl.glGenTextures(5, texture, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[0]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, backgroundImg.width, backgroundImg.height, 0, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE,
                IntBuffer.wrap(backgroundImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        // #####
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[1]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, floorImg.width, floorImg.height, 0, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE,
                IntBuffer.wrap(floorImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        // #####
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[2]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, lWallImg.width, lWallImg.height, 0, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE,
                IntBuffer.wrap(lWallImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        // #####
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[3]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, rWallImg.width, rWallImg.height, 0, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE,
                IntBuffer.wrap(rWallImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        // #####
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[4]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, ceilImg.width, ceilImg.height, 0, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE,
                IntBuffer.wrap(ceilImg.pixels));
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
        for (int i = 0; i < Config.NUM_PARTICLES; i++) {
            particle[i] = new Particle3D(random(leftWall, rightWall), random(floorLevel, ceiling), random(0, backWall), 0, 0, 0);
            particleBuffer.put(particle[i].x);
            particleBuffer.put(particle[i].y);
            particleBuffer.put(particle[i].z);
            particleBuffer.put(particle[i].velX);
            particleBuffer.put(particle[i].velY);
            particleBuffer.put(particle[i].velZ);
            particleColor[i] = color(255);
            colorBuffer.put(red(particleColor[i]));
            colorBuffer.put(green(particleColor[i]));
            colorBuffer.put(blue(particleColor[i]));
            colorBuffer.put(alpha(particleColor[i]));
        }

        particleBuffer.rewind();
        colorBuffer.rewind();
        timestamp = millis() - timestamp;
        System.out.println("OpenCL Buffer Fill time: " + timestamp + " ms");

        timestamp = millis();
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

        clMemParticles = openCL.createBufferFromGLObject(VBOParticleBuffer[0], CLMem.Usage.InputOutput);
        clMemColors = openCL.createBufferFromGLObject(VBOColorBuffer[0], CLMem.Usage.InputOutput);

        clMemCom = new OpenCLBuffer();
        clMemCom.initBuffer(MAX_USERS * 4 * OpenCL.SIZEOF_FLOAT, CLMem.Usage.InputOutput, comBuffer);

        System.out.println("Load particle program");
        openCL.loadProgramFromFile(dataPath(CL_RESSOURCE + "Particle3D.cl"));
        clKernel = openCL.loadKernel("updateParticle");

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

    private void updateMayorMode() {
        progressStory();
    }

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
                    pointCloudSteps = 200;
                    moveBackCounter = 0;
                    drawCount = 1;
                } else {
                    moveBackCounter++;
                }
            } else if (phase == PHASE_3_BUILDING) {
                debugText = "Phase 3";
                if (pointCloudSteps > 500) {
                    pointCloudSteps -= 20;
                } else if (pointCloudSteps > 200) {
                    pointCloudSteps -= 10;
                } else if (pointCloudSteps > 100) {
                    pointCloudSteps -= 5;
                } else if (pointCloudSteps > 50) {
                    pointCloudSteps -= 2;
                } else if (pointCloudSteps > 2) {
                    pointCloudSteps--;
                } else if (pointCloudSteps <= 2) {
                    phase = PHASE_4_BEING;
                    mayorMode = MODE_STATIC;
                    pointCloudSteps = 1;
                    drawCount = 0;
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
                    // mayorMode = MODE_NEG_WEIGHTLESS;
                    // moveBackCounter = 0;
                    drawCount = 1;
                    // } else if (mayorMode == MODE_NEG_WEIGHTLESS
                    // && moveBackCounter >= Config.TIME_EXPANDING * 0.5) {
                    mayorMode = MODE_GRAVITY;
                    gravityMode = GRAVITY_FALLING;
                    howOften = 1000;
                    moveBackCounter = 0;
                    pointCloudSteps = 1000;
                } else if (mayorMode == MODE_GRAVITY && moveBackCounter < Config.TIME_DESINTEGRATING) {
                    if (pointCloudSteps > 500) {
                        pointCloudSteps -= 10;
                    } else if (pointCloudSteps > 200) {
                        pointCloudSteps -= 5;
                    } else if (pointCloudSteps > 100) {
                        pointCloudSteps -= 2;
                        // } else if (pointCloudSteps > 50) {
                        // pointCloudSteps -= 2;
                    } else if (pointCloudSteps > 2) {
                        pointCloudSteps--;
                    } else {
                        moveBackCounter = Config.TIME_DESINTEGRATING;
                    }
                    moveBackCounter++;
                } else if (mayorMode == MODE_GRAVITY && moveBackCounter >= Config.TIME_DESINTEGRATING) {
                    moveBackCounter = 0;
                    mayorMode = MODE_GRAVITY;
                    howOften = 1;
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

    @Override
    public void draw() {

        PGraphicsOpenGL pgl = (PGraphicsOpenGL) g;
        ni.update();

        background(0);

        fpsCounter.triggerCount();

        // set the cam position
        translate(width / 2, height / 2, 0);
        rotateX(camRotX);
        rotateY(camRotY);
        scale(camZoomF);

        rgbImg = ni.rgbImage();

        // pointCloudSteps = 2;
        int userCount = ni.getNumberOfUsers();
        if (userCount > MAX_USERS) {
            userCount = MAX_USERS;
        }

        int[] userMap = null;

        if (userCount > 0) {
            // first assume theres no one beside of userCount
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
            // updateComVBO(userCenters);

            updateMayorMode();
            if (mayorMode == MODE_STATIC) {
                drawBackground();
                // putAllParticlesToGround();
                drawStaticPoints(pgl, pointCloudSteps, userMap);
            } else if (mayorMode == MODE_GRAVITY) {
                drawBackground();
                if (phase == PHASE_5_DESINTEGRATING) {
                    drawStaticPoints(pgl, 2, userMap);
                }
                createFreeMovingParticles(pointCloudSteps, userMap, howOften);
            } else if (mayorMode == MODE_WEIGHTLESS || mayorMode == MODE_NEG_WEIGHTLESS) {
                createFreeMovingParticles(pointCloudSteps, userMap, howOften);
                drawBackground();
            } else if (mayorMode == MODE_RANDOM) {
                makeAllParticlesRandom();
                drawBackground();
            } else if (mayorMode == MODE_PLANETARY || mayorMode == MODE_PLANETARY_BUILD) {
                drawBackground();
                if (phase == PHASE_3_BUILDING) {
                    drawStaticPoints(pgl, pointCloudSteps, userMap);
                }
            }
        } else {
            userInRoom = false;
            phase = PHASE_0_START;
            mayorMode = MODE_GRAVITY;
            gravityMode = GRAVITY_FALLING;
            drawBackground();

        }

        updateComVBO(userCenters);

        Update();
        Render();

        // drawCenterOfMass(userCenters);
        // PFont metaBold = createFont("FFScala", 88);
        //
        // textFont(metaBold, 88);
        // stroke(255);
        // fill(0);
        // text(debugText + "\nUsers: " + userCount, 0, 0);
        // System.out.println("FPS:" + fpsCounter.getFrequency());
    }

    private void updateComVBO(PVector[] centers) {
        if (centers != null) {
            for (int i = 0; i < centers.length; i++) {
                comBuffer.put(i * 4 + 0, centers[i].x);
                comBuffer.put(i * 4 + 1, centers[i].y);
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

    private void drawCenterOfMass(PVector[] centers) {
        if (centers != null) {
            gl = pgl.beginGL();
            gl.glPointSize(8);
            gl.glBegin(GL.GL_POINTS);
            for (int i = 0; i < centers.length; i++) {
                // if (centers[i].x != 0 && centers[i].y != 0 && centers[i].z !=
                // 0) {
                gl.glColor3f(0, 255, 0);
                gl.glVertex3f(centers[i].x, centers[i].y, centers[i].z);
                // }
            }
            gl.glEnd();
            gl.glPointSize(Config.POINTSIZE);
        }
    }

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

    private void createFreeMovingParticles(int pointCloudSteps, int[] userMap, int howOften) {
        PVector realWorldPoint;
        if (userMap != null) {
            if (drawCount < howOften) {
                for (int i = 0; i < userMap.length; i += pointCloudSteps) {
                    realWorldPoint = ni.depthMapRealWorld()[i];
                    int x = i % ni.depthWidth();
                    int y = (i - x) / ni.depthWidth();
                    // check if there is a user
                    if (userMap[i] != 0) {
                        createGravityParticle(realWorldPoint.x + random(-2, 2), realWorldPoint.y + random(-2, 2), realWorldPoint.z
                                + random(-2, 2), x, y);
                    }
                }
            }
            drawCount++;
        }
    }

    public void createGravityParticle(float x, float y, float z, int realX, int realY) {
        if (tmpParticleNumber >= Config.NUM_PARTICLES) {
            tmpParticleNumber = 0;
        }
        createGravityParticle(x, y, z, realX, realY, tmpParticleNumber);
        tmpParticleNumber++;
    }

    public void createGravityParticle(float x, float y, float z, int realX, int realY, int particleNumber) {
        if (z <= 10) {
            return;
        }

        particle[particleNumber].x = x + random(-1.0f, 1.0f);
        particle[particleNumber].y = y + random(-1.0f, 1.0f);
        particle[particleNumber].z = z + random(-1.0f, 1.0f);

        if (gravityMode == GRAVITY_FALLING) {
            // falling mode
            float a = random(0, 180);
            float b = random(0, 360);
            particle[particleNumber].velX = sin(a) * cos(b);
            particle[particleNumber].velY = cos(a);
            particle[particleNumber].velZ = sin(a) * sin(b);
        } else if (gravityMode == GRAVITY_EXPLODE) {
            // explode mode
            float a = random(0, 180);
            float b = random(0, 360);
            particle[particleNumber].velX = 50 * sin(a) * cos(b);
            particle[particleNumber].velY = 50 * cos(a);
            particle[particleNumber].velZ = 50 * sin(a) * sin(b);
        }
        updateParticleToVBO(particleNumber);
        paintParticle(realX, realY, particleNumber);
    }

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
                // check if there is a user
                if (userMap[i] != 0) {
                    gl.glColor3f(norm(red(rgbImg.get(x, y)), 0, 255), norm(green(rgbImg.get(x, y)), 0, 255),
                            norm(blue(rgbImg.get(x, y)), 0, 255));
                    gl.glVertex3f(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
            }

            gl.glEnd();
            gl.glDisable(GL.GL_POINTS);
            pgl.endGL();
        }
    }

    private void paintParticle(int realX, int realY, int particleNumber) {
        paintParticle(rgbImg.get(realX, realY), particleNumber);
    }

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

        clMemColors.read(colorBytes, 0, Config.NUM_PARTICLES * 4 * 4, true);
        colorBuffer = colorBytes.asFloatBuffer();

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

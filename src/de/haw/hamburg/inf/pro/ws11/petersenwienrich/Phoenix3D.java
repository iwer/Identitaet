package de.haw.hamburg.inf.pro.ws11.petersenwienrich;

import static javax.media.opengl.GL.GL_ARRAY_BUFFER_ARB;
import static javax.media.opengl.GL.GL_DYNAMIC_COPY_ARB;

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

    // ParticleConstants
    private final int NUM_PARTICLES = 512 * 128; // 1024 * 256;
    private final int PARTICLE_NFLOAT = 8;
    private final int PARTICLEFLOATSIZE = NUM_PARTICLES * PARTICLE_NFLOAT;
    private final int PARTICLEBYTESIZE = PARTICLEFLOATSIZE * 4;

    // Modes
    private static final int MODE_STATIC = 0;
    private static final int MODE_GRAVITY = 1;
    private static final int MODE_WEIGHTLESS = 2;
    private static final int MODE_NEG_WEIGHTLESS = 3;
    private static final int NMODES = 4;

    private int mayorMode = MODE_STATIC;

    private static final int GRAVITY_FALLING = 102;
    private static final int GRAVITY_EXPLODE = 103;
    private int gravityMode = GRAVITY_EXPLODE;

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

    // stopwatch
    long timestamp;

    private float floorLevel;
    private float leftWall;
    private float rightWall;
    private float backWall;
    private float backGroundWall;

    private int moveBackCounter;

    private final int[] texture = new int[1];

    private final boolean brutal = true;

    @Override
    public void setup() {

        // SimpleOpenNI Setup
        timestamp = millis();
        ni = new SimpleOpenNI(this, SimpleOpenNI.RUN_MODE_MULTI_THREADED);
        ni.setMirror(false);
        ni.enableDepth();
        ni.enableUser(SimpleOpenNI.SKEL_PROFILE_NONE);
        // ni.enableScene();
        ni.enableRGB(ni.depthWidth(), ni.depthHeight(), 30);
        ni.alternativeViewPointDepthToImage();
        timestamp = millis() - timestamp;
        System.out.println("SimpleOpenNI Setup time: " + timestamp + " ms");

        // initialize Processing window:
        timestamp = millis();
        size(1024, 768, OPENGL);
        frameRate(30);
        timestamp = millis() - timestamp;
        System.out.println("Processing Setup time: " + timestamp + " ms");

        floorLevel = -(height + 500);
        leftWall = -3000;
        rightWall = 3000;
        backWall = 5000;
        backGroundWall = 4000;

        backgroundImg = loadImage(IMG_RESSOURCE + "background.jpg");
        int tc = backgroundImg.pixels[0];
        System.out.println("BG Image format: " + backgroundImg.format + "\nred:" + red(tc) + " green:" + green(tc) + " blue:" + blue(tc)
                + " alpha:" + alpha(tc));

        // initialize GL object
        timestamp = millis();
        pgl = (PGraphicsOpenGL) g;
        gl = pgl.beginGL();

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);

        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE);

        gl.glGenTextures(1, texture, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[0]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, backgroundImg.width, backgroundImg.height, 0, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE,
                IntBuffer.wrap(backgroundImg.pixels));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

        // gl.glBindTexture(GL.GL_TEXTURE_2D, texture[0]);
        // gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, backgroundImg.width,
        // backgroundImg.height, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE,
        // IntBuffer.wrap(backgroundImg.pixels));
        // gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

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

        // init particles
        timestamp = millis();
        particle = new Particle3D[NUM_PARTICLES];
        particleBytes = openCL.createByteBuffer(PARTICLEBYTESIZE);
        particleBuffer = particleBytes.asFloatBuffer();

        particleColor = new int[NUM_PARTICLES];
        colorBytes = openCL.createByteBuffer(NUM_PARTICLES * 4 * OpenCL.SIZEOF_FLOAT);
        colorBuffer = colorBytes.asFloatBuffer();

        timestamp = millis() - timestamp;
        System.out.println("OpenCL Buffer Setup time: " + timestamp + " ms");

        timestamp = millis();
        for (int i = 0; i < NUM_PARTICLES; i++) {
            particle[i] = new Particle3D(0, 0, 0, 0, 0, 0);
            particleBuffer.put(particle[i].x);
            particleBuffer.put(particle[i].y);
            particleBuffer.put(particle[i].z);
            particleBuffer.put(particle[i].velX);
            particleBuffer.put(particle[i].velY);
            particleBuffer.put(particle[i].velZ);
            particleColor[i] = color(0);
            colorBuffer.put(red(particleColor[i]));
            colorBuffer.put(green(particleColor[i]));
            colorBuffer.put(blue(particleColor[i]));
            colorBuffer.put(0);
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

        System.out.println("Load particle program");
        openCL.loadProgramFromFile(dataPath(CL_RESSOURCE + "Particle3D.cl"));
        clKernel = openCL.loadKernel("updateParticle");

        clKernel.setArg(0, clMemParticles.getCLMem());
        clKernel.setArg(1, clMemColors.getCLMem());
        clKernel.setArg(2, floorLevel);
        clKernel.setArg(3, leftWall);
        clKernel.setArg(4, rightWall);
        clKernel.setArg(5, backWall);
        clKernel.setArg(6, mayorMode);

        gl.glPointSize(5);
        pgl.endGL();
        perspective(95, width / height, 10, 150000);

    }

    @Override
    public void draw() {
        PGraphicsOpenGL pgl = (PGraphicsOpenGL) g;
        ni.update();

        fpsCounter.triggerCount();

        // set the cam position
        translate(width / 2, height / 2, 0);
        // camRotTick = (camRotTick + 0.1f) % 12.8f;
        // rotX = radians(180) + radians(sin(2 * tick));
        // rotY = radians(0) + 10 * radians(cos(tick / 2));
        rotateX(camRotX);
        rotateY(camRotY);
        scale(camZoomF);

        rgbImg = ni.rgbImage();

        int pointCloudSteps = 2; // to speed up the drawing, draw every third
                                 // point
                                 // drawBackground();
        int userCount = ni.getNumberOfUsers();
        int[] userMap = null;
        if (userCount > 0) {
            userMap = ni.getUsersPixels(SimpleOpenNI.USERS_ALL);
            int lastChangedParticleNr;
            int firstChangedParticleNr = tmpParticleNumber;
            if (mayorMode == MODE_STATIC) {
                drawBackground();
                drawStaticPoints(pgl, pointCloudSteps, userMap);
            } else if (mayorMode == MODE_GRAVITY || mayorMode == MODE_WEIGHTLESS || mayorMode == MODE_NEG_WEIGHTLESS) {
                createFreeMovingParticles(pointCloudSteps, userMap);
                drawBackground();
            }
            lastChangedParticleNr = tmpParticleNumber;

            updateMayorMode();
            Update(firstChangedParticleNr, lastChangedParticleNr);

        } else {
            drawBackground();
        }

        drawRoom();
        Render();

        System.out.println("FPS:" + fpsCounter.getFrequency());
    }

    private void createFreeMovingParticles(int pointCloudSteps, int[] userMap) {
        PVector realWorldPoint;
        if (userMap != null) {
            if (drawCount <= 1) {
                for (int i = 0; i < userMap.length; i += pointCloudSteps) {
                    realWorldPoint = ni.depthMapRealWorld()[i];
                    int x = i % ni.depthWidth();
                    int y = (i - x) / ni.depthHeight();
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

    private void updateMayorMode() {
        if (mayorMode == MODE_WEIGHTLESS) {
            if (moveBackCounter >= 80) {
                mayorMode = MODE_NEG_WEIGHTLESS;
                moveBackCounter = 0;
            } else {
                moveBackCounter++;
            }
        } else if (mayorMode == MODE_NEG_WEIGHTLESS) {
            if (moveBackCounter >= 2) {
                mayorMode = MODE_GRAVITY;
                gravityMode = GRAVITY_FALLING;
                moveBackCounter = 0;
            } else {
                moveBackCounter++;
            }
        }
    }

    private void drawStaticPoints(PGraphicsOpenGL pgl, int pointCloudSteps, int[] userMap) {
        PVector realWorldPoint;
        if (userMap != null) {
            gl = pgl.beginGL();
            gl.glEnable(GL.GL_POINTS);
            gl.glBegin(GL.GL_POINTS);
            // colorMode(RGB, 255);
            for (int i = 0; i < userMap.length; i += pointCloudSteps) {
                realWorldPoint = ni.depthMapRealWorld()[i];
                int x = i % ni.depthWidth();
                int y = (i - x) / ni.depthHeight();
                // check if there is a user
                if (userMap[i] != 0) {
                    // gl.glColor3f(255, 255, 255);
                    // gl.glColor3f(norm(random(255), 0, 255),
                    // norm(random(255), 0, 255), norm(random(255), 0,
                    // 255));
                    gl.glColor3f(norm(red(rgbImg.get(x, y - 50)), 0, 255), norm(green(rgbImg.get(x, y - 50)), 0, 255),
                            norm(blue(rgbImg.get(x, y - 50)), 0, 255));

                    // gl.glColor3f(1.0f, 0, 0);
                    gl.glVertex3f(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
            }
            gl.glEnd();
            gl.glDisable(GL.GL_POINTS);
            pgl.endGL();
        }
    }

    public void drawBackground() {
        background(0);
        gl = pgl.beginGL();

        gl.glEnable(GL.GL_TEXTURE_2D);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[0]);

        gl.glBegin(GL.GL_QUADS);
        gl.glTexCoord2f(0f, 0f);
        gl.glVertex3f(-(backgroundImg.width * 3), (backgroundImg.height * 3), backGroundWall);
        gl.glTexCoord2f(1f, 0f);
        gl.glVertex3f((backgroundImg.width * 3), (backgroundImg.height * 3), backGroundWall);
        gl.glTexCoord2f(1f, 1f);
        gl.glVertex3f((backgroundImg.width * 3), -(backgroundImg.height * 3), backGroundWall);
        gl.glTexCoord2f(0f, 1f);
        gl.glVertex3f(-(backgroundImg.width * 3), -(backgroundImg.height * 3), backGroundWall);

        gl.glEnd();
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glDisable(GL.GL_TEXTURE_2D);
        pgl.endGL();
    }

    public void drawRoom() {
        gl = pgl.beginGL();
        float c = 255;
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glColor3f(c, c, c);
        gl.glTexCoord2f(0, 0);
        gl.glVertex3f(leftWall, floorLevel + .5f, 0);
        gl.glTexCoord2f(1, 0);
        gl.glVertex3f(rightWall, floorLevel + .5f, 0);
        gl.glTexCoord2f(1, 1);
        gl.glVertex3f(rightWall, floorLevel + .5f, backWall);
        gl.glTexCoord2f(0, 1);
        gl.glVertex3f(leftWall, floorLevel + .5f, backWall);
        gl.glEnd();

        gl.glBegin(GL.GL_LINES);
        gl.glColor3f(c, c, c);
        gl.glVertex3f(leftWall, floorLevel + .5f, 0);
        gl.glVertex3f(leftWall, 2000, 0);
        gl.glVertex3f(leftWall, floorLevel + .5f, backWall);
        gl.glVertex3f(leftWall, 2000, backWall);
        gl.glVertex3f(rightWall, floorLevel + .5f, 0);
        gl.glVertex3f(rightWall, 2000, 0);
        gl.glVertex3f(rightWall, floorLevel + .5f, backWall);
        gl.glVertex3f(rightWall, 2000, backWall);
        gl.glEnd();

        pgl.endGL();
    }

    public void createWeightlessParticle(float x, float y, float z, int realX, int realY) {
        createWeightlessParticle(x, y, z, realX, realY, tmpParticleNumber);
    }

    public void createWeightlessParticle(float x, float y, float z, int realX, int realY, int particleNumber) {
        if (z <= 10) {
            return;
        }

        if (particleNumber >= NUM_PARTICLES) {
            particleNumber = 0;
        }
        particle[particleNumber].x = x + random(-1.0f, 1.0f);
        particle[particleNumber].y = y + random(-1.0f, 1.0f);
        particle[particleNumber].z = z + random(-1.0f, 1.0f);

        if (mayorMode == MODE_WEIGHTLESS) {
            float a = random(0, 180);
            float b = random(0, 360);
            particle[particleNumber].velX = 3 * sin(a) * cos(b);
            particle[particleNumber].velY = 3 * cos(a);
            particle[particleNumber].velZ = 3 * sin(a) * sin(b);
        }

        paintParticle(realX, realY, particleNumber);
    }

    public void createGravityParticle(float x, float y, float z, int realX, int realY) {
        if (tmpParticleNumber >= NUM_PARTICLES) {
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

        paintParticle(realX, realY, particleNumber);

    }

    private void paintParticle(int realX, int realY, int particleNumber) {
        particleColor[particleNumber] = color(rgbImg.get(realX, realY));
        if (particleNumber % 10 == 0 && brutal && gravityMode == GRAVITY_EXPLODE) {
            particleColor[particleNumber] = color(255, 0, 0);
        }
        // color[particleNumber] = color(random(255), random(255), random(255),
        // random(255));
        // color[particleNumber] = color(255);

        colorBuffer.put(particleNumber * 4 + 0, norm(red(particleColor[particleNumber]), 0, 255));
        colorBuffer.put(particleNumber * 4 + 1, norm(green(particleColor[particleNumber]), 0, 255));
        colorBuffer.put(particleNumber * 4 + 2, norm(blue(particleColor[particleNumber]), 0, 255));
        colorBuffer.put(particleNumber * 4 + 3, norm(alpha(particleColor[particleNumber]), 0, 255));
    }

    void Render() {
        //
        // PARTICLES DEMO
        //
        gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, VBOColorBuffer[0]);
        gl.glEnableClientState(GL.GL_COLOR_ARRAY);
        gl.glColorPointer(4, GL.GL_FLOAT, 0, 0);

        gl = pgl.beginGL();

        // vgl.ortho( App.WIDTH, App.HEIGHT );

        // gl.glColor3f(255, 255, 255);
        openCL.finish();

        gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, VBOParticleBuffer[0]);

        gl.glPushMatrix();
        gl.glEnableClientState(GL.GL_VERTEX_ARRAY);
        gl.glVertexPointer(3, GL.GL_FLOAT, 32, 0);
        gl.glDrawArrays(GL.GL_POINTS, 0, NUM_PARTICLES);
        gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, 0);
        gl.glDisableClientState(GL.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL.GL_COLOR_ARRAY);
        gl.glPopMatrix();
        pgl.endGL();

    }

    void Update(int firstPart, int lastPart) {
        openCL.acquireGLObjects();

        clMemParticles.read(particleBytes, 0, PARTICLEBYTESIZE, true);
        particleBuffer = particleBytes.asFloatBuffer();
        //

        // int index;
        // if (count == 0) {
        // index = NUMPARTICLES - 1;
        // } else {
        // index = count - 1;
        // }
        gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, VBOColorBuffer[0]);
        // gl.glBufferSubDataARB(GL_ARRAY_BUFFER_ARB, index, (lastPart -
        // firstPart) * 4, colorBuffer);
        gl.glBufferDataARB(GL_ARRAY_BUFFER_ARB, PARTICLEBYTESIZE, colorBuffer, GL_DYNAMIC_COPY_ARB);
        gl.glBindBufferARB(GL.GL_ARRAY_BUFFER_ARB, 0);

        if (firstPart <= lastPart) {
            for (int i = firstPart; i < lastPart; i++) {
                particleBuffer.put(i * PARTICLE_NFLOAT + 0, particle[i].x);
                particleBuffer.put(i * PARTICLE_NFLOAT + 1, particle[i].y);
                particleBuffer.put(i * PARTICLE_NFLOAT + 2, particle[i].z);
                particleBuffer.put(i * PARTICLE_NFLOAT + 3, particle[i].velX);
                particleBuffer.put(i * PARTICLE_NFLOAT + 4, particle[i].velY);
                particleBuffer.put(i * PARTICLE_NFLOAT + 5, particle[i].velZ);
                // particleBuffer.put(i * PARTICLENFLOAT + 6, p[i].dummy1);
                // particleBuffer.put(i * PARTICLENFLOAT + 7, p[i].dummy2);
            }
        } else {
            for (int i = firstPart; i < NUM_PARTICLES; i++) {
                particleBuffer.put(i * PARTICLE_NFLOAT + 0, particle[i].x);
                particleBuffer.put(i * PARTICLE_NFLOAT + 1, particle[i].y);
                particleBuffer.put(i * PARTICLE_NFLOAT + 2, particle[i].z);
                particleBuffer.put(i * PARTICLE_NFLOAT + 3, particle[i].velX);
                particleBuffer.put(i * PARTICLE_NFLOAT + 4, particle[i].velY);
                particleBuffer.put(i * PARTICLE_NFLOAT + 5, particle[i].velZ);
                // particleBuffer.put(i * PARTICLENFLOAT + 6, p[i].dummy1);
                // particleBuffer.put(i * PARTICLENFLOAT + 7, p[i].dummy2);
            }
            for (int i = 0; i < lastPart; i++) {
                particleBuffer.put(i * PARTICLE_NFLOAT + 0, particle[i].x);
                particleBuffer.put(i * PARTICLE_NFLOAT + 1, particle[i].y);
                particleBuffer.put(i * PARTICLE_NFLOAT + 2, particle[i].z);
                particleBuffer.put(i * PARTICLE_NFLOAT + 3, particle[i].velX);
                particleBuffer.put(i * PARTICLE_NFLOAT + 4, particle[i].velY);
                particleBuffer.put(i * PARTICLE_NFLOAT + 5, particle[i].velZ);
                // particleBuffer.put(i * PARTICLENFLOAT + 6, p[i].dummy1);
                // particleBuffer.put(i * PARTICLENFLOAT + 7, p[i].dummy2);
            }
        }
        particleBuffer.rewind();

        clMemParticles.write(particleBytes, 0, PARTICLEBYTESIZE, true);
        // /////////////////////////////////////////
        try {
            long wgs = 0;
            // long wgs = 32;
            // long maxwgs = openCL.getDevice().getMaxWorkGroupSize();
            // if (wgs > maxwgs) wgs = maxwgs;

            clKernel.setArg(0, clMemParticles.getCLMem());
            clKernel.setArg(1, clMemColors.getCLMem());
            clKernel.setArg(2, floorLevel);
            clKernel.setArg(3, leftWall);
            clKernel.setArg(4, rightWall);
            clKernel.setArg(5, backWall);
            clKernel.setArg(6, mayorMode);

            clKernel.run1D(NUM_PARTICLES, (int) wgs);
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        // if( openCL.isSharingContext() )
        // clMemPosVBO.getCLMem().releaseGLObject( openCL.getQueue() );

        clMemColors.read(colorBytes, 0, NUM_PARTICLES * 4 * 4, true);
        colorBuffer = colorBytes.asFloatBuffer();
        openCL.releaseGLObjects();
    }

    // Keyboard events

    @Override
    public void mousePressed() {
        if (mayorMode == MODE_STATIC && gravityMode == GRAVITY_EXPLODE) {
            mayorMode = MODE_WEIGHTLESS;
            gravityMode = GRAVITY_FALLING;
        } else if (mayorMode == MODE_GRAVITY && gravityMode == GRAVITY_FALLING) {
            mayorMode = MODE_STATIC;
            drawCount = 0;
        } else if (mayorMode == MODE_STATIC && gravityMode == GRAVITY_FALLING) {
            mayorMode = MODE_GRAVITY;
            gravityMode = GRAVITY_EXPLODE;
        } else if (mayorMode == MODE_GRAVITY && gravityMode == GRAVITY_EXPLODE) {
            mayorMode = MODE_STATIC;
            drawCount = 0;
        }
        // mode = (mode + 1) % NMODES;
        // if (mode == MODE_GRAVITY) {
        //
        // }
        // System.out.println("Mode: " + mode);
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
}

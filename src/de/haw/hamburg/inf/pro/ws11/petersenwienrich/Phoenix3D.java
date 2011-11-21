package de.haw.hamburg.inf.pro.ws11.petersenwienrich;

import static javax.media.opengl.GL.GL_ARRAY_BUFFER_ARB;
import static javax.media.opengl.GL.GL_DYNAMIC_COPY_ARB;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

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

    private int mode = MODE_STATIC;

    private static final int MODE_GRAVITY_FALLING = 102;
    private static final int MODE_GRAVITY_EXPLODE = 103;
    private int gravityMode = MODE_GRAVITY_EXPLODE;

    // Ressources Location
    private static final String RESSOURCE = Config.CL_LOCATION;

    // Open*L Objects
    private OpenCL openCL;
    private PGraphicsOpenGL pgl;
    private GL gl;
    private OpenCLKernel kernel;

    // FrameCounter
    private final FrequencyCounter fps = new FrequencyCounter();

    // Particle Buffers
    private Particle3D[] p;
    private ByteBuffer particleBytes;
    private FloatBuffer particleBuffer;
    private OpenCLBuffer clMemParticles;
    private int[] VBOParticleBuffer;

    // ColorBuffers
    private int[] color;
    private ByteBuffer colorBytes;
    private FloatBuffer colorBuffer;
    private OpenCLBuffer clMemColors;
    private int[] VBOColorBuffer;

    // SimpleOpenNI Objects
    private SimpleOpenNI ni;
    private float zoomF = 0.15f;
    private float rotX = radians(180) - 0.4f; // by default rotate the whole
                                              // scene 180deg around
    // the x-axis,
    // the data from openni comes upside down
    private float rotY = radians(0);
    private float tick;

    // Moved Particle Counter
    private int count;

    // draw counter
    private int run;

    // for the user colors
    private PImage img;

    // stopwatch
    long time;

    private float floorLevel;
    private float leftWall;
    private float rightWall;
    private float backWall;

    private final int xSize = 500;

    private int counter;

    @Override
    public void setup() {

        // SimpleOpenNI Setup
        time = millis();
        ni = new SimpleOpenNI(this, SimpleOpenNI.RUN_MODE_MULTI_THREADED);
        ni.setMirror(false);
        ni.enableDepth();
        ni.enableUser(SimpleOpenNI.SKEL_PROFILE_NONE);
        // ni.enableScene();
        ni.enableRGB(ni.depthWidth(), ni.depthHeight(), 30);
        // ni.alternativeViewPointDepthToImage();
        time = millis() - time;
        System.out.println("SimpleOpenNI Setup time: " + time + " ms");

        // initialize Processing window:
        time = millis();
        size(1024, 768, OPENGL);
        frameRate(30);
        time = millis() - time;
        System.out.println("Processing Setup time: " + time + " ms");

        floorLevel = -(height + 500);
        leftWall = -2000;
        rightWall = 2000;
        backWall = 4000;

        // initialize GL object
        time = millis();
        pgl = (PGraphicsOpenGL) g;
        gl = pgl.beginGL();

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        // gl.glDepthMask(true);
        // gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE);

        // //LIGHT
        // gl.glEnable(GL.GL_LIGHTING);
        // float light1_pos_arr[] = { 0, -4, 5, 1 };
        // FloatBuffer light1_pos = FloatBuffer.wrap(light1_pos_arr);
        // float light1_color_am_arr[] = { 0, 0, 1, 1 };
        // FloatBuffer light1_color_am = FloatBuffer.wrap(light1_color_am_arr);
        // float light1_color_diff_arr[] = { 1, 0, 0, 1 };
        // FloatBuffer light1_color_diff =
        // FloatBuffer.wrap(light1_color_diff_arr);
        // float light1_color_spec_arr[] = { 1, 1, 1, 1 };
        // FloatBuffer light1_color_spec =
        // FloatBuffer.wrap(light1_color_spec_arr);
        //
        // gl.glLightfv(GL.GL_LIGHT1, GL.GL_POSITION, light1_pos);
        // gl.glLightfv(GL.GL_LIGHT1, GL.GL_AMBIENT, light1_color_am);
        // gl.glLightfv(GL.GL_LIGHT1, GL.GL_DIFFUSE, light1_color_diff);
        // gl.glLightfv(GL.GL_LIGHT1, GL.GL_SPECULAR, light1_color_spec);
        // //FOG
        // float[] fogColorArr = { 0.5f, 0.5f, 0.5f, 1.0f }; // Fog Color
        // FloatBuffer fogColor = FloatBuffer.wrap(fogColorArr); //
        // gl.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
        // gl.glEnable(GL.GL_FOG); // Enables GL_FOG
        // gl.glFogi(GL.GL_FOG_MODE, GL.GL_LINEAR); // Fog Mode
        // gl.glFogfv(GL.GL_FOG_COLOR, fogColor); // Set Fog Color
        // gl.glFogf(GL.GL_FOG_DENSITY, 0.35f); // How Dense Will The Fog Be
        // gl.glHint(GL.GL_FOG_HINT, GL.GL_DONT_CARE); // Fog Hint Value
        // gl.glFogf(GL.GL_FOG_START, 1.0f); // Fog Start Depth
        // gl.glFogf(GL.GL_FOG_END, 5.0f); // Fog End Depth
        time = millis() - time;
        System.out.println("OpenGL Setup time: " + time + " ms");

        // init openCL
        time = millis();
        openCL = OpenCL.getSingleton();
        openCL.setupFromOpenGL();
        // openCL.setup(OpenCL.GPU);
        time = millis() - time;
        System.out.println("OpenCL Setup time: " + time + " ms");

        System.out.println("Interoperability: " + openCL.isSharingContext());

        count = 0;

        // init particles
        time = millis();
        p = new Particle3D[NUM_PARTICLES];
        particleBytes = openCL.createByteBuffer(PARTICLEBYTESIZE);
        particleBuffer = particleBytes.asFloatBuffer();

        color = new int[NUM_PARTICLES];
        colorBytes = openCL.createByteBuffer(NUM_PARTICLES * 4 * OpenCL.SIZEOF_FLOAT);
        colorBuffer = colorBytes.asFloatBuffer();

        time = millis() - time;
        System.out.println("OpenCL Buffer Setup time: " + time + " ms");

        time = millis();
        for (int i = 0; i < NUM_PARTICLES; i++) {
            p[i] = new Particle3D(0, 0, 0, 0, 0, 0);
            particleBuffer.put(p[i].x);
            particleBuffer.put(p[i].y);
            particleBuffer.put(p[i].z);
            particleBuffer.put(p[i].velX);
            particleBuffer.put(p[i].velY);
            particleBuffer.put(p[i].velZ);
            color[i] = color(0);
            colorBuffer.put(red(color[i]));
            colorBuffer.put(green(color[i]));
            colorBuffer.put(blue(color[i]));
            colorBuffer.put(0);
        }

        particleBuffer.rewind();
        colorBuffer.rewind();
        time = millis() - time;
        System.out.println("OpenCL Buffer Fill time: " + time + " ms");

        time = millis();
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
        time = millis() - time;
        System.out.println("OpenCL VBO Setup time: " + time + " ms");

        clMemParticles = openCL.createBufferFromGLObject(VBOParticleBuffer[0], CLMem.Usage.InputOutput);
        clMemColors = openCL.createBufferFromGLObject(VBOColorBuffer[0], CLMem.Usage.InputOutput);

        System.out.println("Load particle program");
        openCL.loadProgramFromFile(dataPath(RESSOURCE + "Particle3D.cl"));
        kernel = openCL.loadKernel("updateParticle");

        kernel.setArg(0, clMemParticles.getCLMem());
        kernel.setArg(1, clMemColors.getCLMem());
        kernel.setArg(2, floorLevel);
        kernel.setArg(3, leftWall);
        kernel.setArg(4, rightWall);
        kernel.setArg(5, backWall);
        kernel.setArg(6, mode);

        gl.glPointSize(5);
        pgl.endGL();
        perspective(95, width / height, 10, 150000);

    }

    @Override
    public void draw() {
        PGraphicsOpenGL pgl = (PGraphicsOpenGL) g;
        ni.update();
        fps.triggerCount();
        tick = (tick + 0.1f) % 12.8f;
        // set the scene pos
        translate(width / 2, height / 2, 0);
        // rotX = radians(180) + radians(sin(2 * tick));
        // rotY = radians(0) + 10 * radians(cos(tick / 2));
        rotateX(rotX);
        rotateY(rotY);
        scale(zoomF);

        img = ni.rgbImage();

        int steps = 2; // to speed up the drawing, draw every third point

        PVector realWorldPoint;
        int userCount = ni.getNumberOfUsers();
        int[] userMap = null;
        if (userCount > 0) {
            userMap = ni.getUsersPixels(SimpleOpenNI.USERS_ALL);
        }
        int lastPart;
        int firstPart = count;
        if (mode == MODE_STATIC) {
            background(255);
            // count = firstPart = 0;
            // #############################

            if (userMap != null) {
                gl = pgl.beginGL();
                // gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
                gl.glBegin(GL.GL_POINTS);
                colorMode(RGB, 255);
                for (int i = 0; i < userMap.length; i += steps) {
                    realWorldPoint = ni.depthMapRealWorld()[i];
                    int x = i % ni.depthWidth();
                    int y = (i - x) / ni.depthHeight();
                    // check if there is a user
                    if (userMap[i] != 0) {
                        // createParticle(realWorldPoint.x + random(-2, 2),
                        // realWorldPoint.y + random(-2, 2),
                        // realWorldPoint.z + random(-2, 2), x, y);

                        // stroke(255);
                        // strokeWeight(1);
                        // point(realWorldPoint.x + random(-2, 2),
                        // realWorldPoint.y + random(-2, 2), realWorldPoint.z
                        // + random(-2, 2));
                        // gl.glColor3f(255, 255, 255);
                        // gl.glColor3f(norm(random(255), 0, 255),
                        // norm(random(255), 0, 255), norm(random(255), 0,
                        // 255));
                        gl.glColor3f(norm(red(img.get(x, y - 50)), 0, 255), norm(green(img.get(x, y - 50)), 0, 255), norm(blue(img.get(x, y - 50)), 0, 255));
                        // gl.glColor3f(1.0f, 0, 0);
                        gl.glVertex3f(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                    }
                }
                gl.glEnd();
                pgl.endGL();
            }

        } else if (mode == MODE_GRAVITY || mode == MODE_WEIGHTLESS || mode == MODE_NEG_WEIGHTLESS) {
            // #############################
            if (userMap != null) {
                if (run <= 1) {
                    for (int i = 0; i < userMap.length; i += steps) {
                        realWorldPoint = ni.depthMapRealWorld()[i];
                        int x = i % ni.depthWidth();
                        int y = (i - x) / ni.depthHeight();
                        // check if there is a user
                        if (userMap[i] != 0) {
                            createGravityParticle(realWorldPoint.x + random(-2, 2), realWorldPoint.y + random(-2, 2), realWorldPoint.z + random(-2, 2), x, y);
                        }
                    }
                }
                run++;
            }
            // #############################
            background(255);
            if (mode == MODE_WEIGHTLESS) {
                if (counter >= 50) {
                    mode = MODE_NEG_WEIGHTLESS;
                    counter = 0;
                } else {
                    counter++;
                }
            } else if (mode == MODE_NEG_WEIGHTLESS) {
                if (counter >= 10) {
                    mode = MODE_GRAVITY;
                    gravityMode = MODE_GRAVITY_FALLING;
                    counter = 0;
                } else {
                    counter++;
                }
            }
        }
        lastPart = count;

        drawRoom();
        if (mode == MODE_GRAVITY) {
            Update(firstPart, lastPart);
        } else {
            Update(firstPart, lastPart);
        }
        Render();
        System.out.println("FPS:" + fps.getFrequency());
    }

    public void drawRoom() {
        pgl.beginGL();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glColor3f(0, 0, 0);
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
        gl.glColor3f(0, 0, 0);
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

    public void createGravityParticle(float x, float y, float z, int realX, int realY) {
        float inverseX = x;
        float inverseY = y;
        float inverseZ = z;

        if (z <= 10) {
            return;
        }

        if (count >= NUM_PARTICLES) {
            count = 0;
        }

        p[count].x = inverseX + random(-1.0f, 1.0f);
        p[count].y = inverseY + random(-1.0f, 1.0f);
        p[count].z = inverseZ + random(-1.0f, 1.0f);

        if (mode == MODE_WEIGHTLESS) {
            float a = random(0, 180);
            float b = random(0, 360);
            p[count].velX = 3 * sin(a) * cos(b);
            p[count].velY = 3 * cos(a);
            p[count].velZ = 3 * sin(a) * sin(b);
        } else if (gravityMode == MODE_GRAVITY_FALLING) {
            // falling mode
            float a = random(0, 180);
            float b = random(0, 360);
            p[count].velX = sin(a) * cos(b);
            p[count].velY = cos(a);
            p[count].velZ = sin(a) * sin(b);
        } else if (gravityMode == MODE_GRAVITY_EXPLODE) {
            // explode mode
            float a = random(0, 180);
            float b = random(0, 360);
            p[count].velX = 50 * sin(a) * cos(b);
            p[count].velY = 50 * cos(a);
            p[count].velZ = 50 * sin(a) * sin(b);
        }

        color[count] = color(img.get(realX, realY));
        // color[count] = color(random(255), random(255), random(255),
        // random(255));
        // color[count] = color(255);

        colorBuffer.put(count * 4 + 0, norm(red(color[count]), 0, 255));
        colorBuffer.put(count * 4 + 1, norm(green(color[count]), 0, 255));
        colorBuffer.put(count * 4 + 2, norm(blue(color[count]), 0, 255));
        colorBuffer.put(count * 4 + 3, norm(alpha(color[count]), 0, 255));
        // colorBuffer.put(count * 4 + 3, norm(127, 0, 255));
        count++;
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
                particleBuffer.put(i * PARTICLE_NFLOAT + 0, p[i].x);
                particleBuffer.put(i * PARTICLE_NFLOAT + 1, p[i].y);
                particleBuffer.put(i * PARTICLE_NFLOAT + 2, p[i].z);
                particleBuffer.put(i * PARTICLE_NFLOAT + 3, p[i].velX);
                particleBuffer.put(i * PARTICLE_NFLOAT + 4, p[i].velY);
                particleBuffer.put(i * PARTICLE_NFLOAT + 5, p[i].velZ);
                // particleBuffer.put(i * PARTICLENFLOAT + 6, p[i].dummy1);
                // particleBuffer.put(i * PARTICLENFLOAT + 7, p[i].dummy2);
            }
        } else {
            for (int i = firstPart; i < NUM_PARTICLES; i++) {
                particleBuffer.put(i * PARTICLE_NFLOAT + 0, p[i].x);
                particleBuffer.put(i * PARTICLE_NFLOAT + 1, p[i].y);
                particleBuffer.put(i * PARTICLE_NFLOAT + 2, p[i].z);
                particleBuffer.put(i * PARTICLE_NFLOAT + 3, p[i].velX);
                particleBuffer.put(i * PARTICLE_NFLOAT + 4, p[i].velY);
                particleBuffer.put(i * PARTICLE_NFLOAT + 5, p[i].velZ);
                // particleBuffer.put(i * PARTICLENFLOAT + 6, p[i].dummy1);
                // particleBuffer.put(i * PARTICLENFLOAT + 7, p[i].dummy2);
            }
            for (int i = 0; i < lastPart; i++) {
                particleBuffer.put(i * PARTICLE_NFLOAT + 0, p[i].x);
                particleBuffer.put(i * PARTICLE_NFLOAT + 1, p[i].y);
                particleBuffer.put(i * PARTICLE_NFLOAT + 2, p[i].z);
                particleBuffer.put(i * PARTICLE_NFLOAT + 3, p[i].velX);
                particleBuffer.put(i * PARTICLE_NFLOAT + 4, p[i].velY);
                particleBuffer.put(i * PARTICLE_NFLOAT + 5, p[i].velZ);
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

            kernel.setArg(0, clMemParticles.getCLMem());
            kernel.setArg(1, clMemColors.getCLMem());
            kernel.setArg(2, floorLevel);
            kernel.setArg(3, leftWall);
            kernel.setArg(4, rightWall);
            kernel.setArg(5, backWall);
            kernel.setArg(6, mode);

            kernel.run1D(NUM_PARTICLES, (int) wgs);
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
        if (mode == MODE_STATIC && gravityMode == MODE_GRAVITY_EXPLODE) {
            mode = MODE_WEIGHTLESS;
            gravityMode = MODE_GRAVITY_FALLING;
        } else if (mode == MODE_GRAVITY && gravityMode == MODE_GRAVITY_FALLING) {
            mode = MODE_STATIC;
            run = 0;
        } else if (mode == MODE_STATIC && gravityMode == MODE_GRAVITY_FALLING) {
            mode = MODE_GRAVITY;
            gravityMode = MODE_GRAVITY_EXPLODE;
        } else if (mode == MODE_GRAVITY && gravityMode == MODE_GRAVITY_EXPLODE) {
            mode = MODE_STATIC;
            run = 0;
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
            gravityMode = MODE_GRAVITY_EXPLODE;
            break;
        case 'f':
            gravityMode = MODE_GRAVITY_FALLING;
            break;
        }

        switch (keyCode) {

        case LEFT:
            rotY += 0.1f;
            break;
        case RIGHT:
            // zoom out
            rotY -= 0.1f;
            break;
        case UP:
            if (keyEvent.isShiftDown())
                zoomF += 0.01f;
            else
                rotX += 0.1f;
            break;
        case DOWN:
            if (keyEvent.isShiftDown()) {
                zoomF -= 0.01f;
                if (zoomF < 0.01)
                    zoomF = 0.01f;
            } else
                rotX -= 0.1f;
            break;
        }
    }
}

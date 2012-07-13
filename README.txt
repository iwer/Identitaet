Requirements:
----------------
You will need a powerfull graphics card!

    Java 1.6
    Processing http://processing.org
    SimpleOpenNI http://code.google.com/p/simple-openni/
    OpenCL via http://code.google.com/p/victamin/downloads/detail?name=msaopencl.zip&can=2&q=  

---------------------

Follow the instructions at http://code.google.com/p/simple-openni/wiki/Installation to get the kinect working with java/processing.

---------------------

Make a new Java Project in Eclipse and import the source.

Be sure to have

    SimpleOpenNI.jar
    msaopencl.jar, javacl-1.0-beta-5-shaded.jar
    from processing:
        ./lib/core.jar
        ./modes/java/libraries/opengl/gluegen-rt.jar
        ./modes/java/libraries/opengl/jogl.jar
        ./modes/java/libraries/opengl/opengl.jar 

in your buildpath. Don't forget to include the required native libraries for SimpleOpenNi, gluegen and jogl.

---------------------

Correct the PROJECT_HOME variable in Config.java

Now the project should build and run properly.
 
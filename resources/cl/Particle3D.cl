
#ifndef __OPENCL_VERSION__
    #define __kernel
    #define __global
#endif

#define GRAVITY -0.981f
#define DELTATIME 2.0f

#define MODE_GRAVITY 1
#define MODE_WEIGHTLESS 2
#define MODE_NEG_WEIGHTLESS 3
#define MODE_RANDOM 4
#define MODE_PLANETARY 5

typedef struct{
    float x;
    float y;
    float z;
    float velX;
    float velY;
    float velZ;
    float dir;
    float dummy2;
} Particle3D;

typedef struct {
	float r;
	float g;
	float b;
	float a; 
} Color;

typedef struct {
    float x;
    float y;
    float z;
    float dummy;
} Com;

__kernel void updateParticle(__global Particle3D* pIn, 
                             __global Color* cIn,
                             __global Com* comIn,
                             float floor, 
                             float ceiling,
                             float leftWall, 
                             float rightWall, 
                             float backWall, 
                             int mode,
                             float forceFactor) {
    // Global work-item ID value
    int id = get_global_id(0);
    // Particle at index id
    __global Particle3D *pin = &pIn[id];
    __global Color *cin = &cIn[id];
    
    float diffSpeedY = GRAVITY * DELTATIME;

    //###########################################################
    // GRAVITY MODE
    
    if(mode == MODE_GRAVITY) {
        if(pin->dir == 1) {
            pin->dir = 0;
        }
        // rightWall contact || leftWall contact flips X velocity
        if (((pin->x) + (pin->velX) > rightWall) ||
            ((pin->x) + (pin->velX) < leftWall)) {
            pin->velX = -(pin->velX);
        }
    
        // backWall contact || frontWall contact flips Z velocity
        if (((pin->z) + (pin->velZ) > backWall) ||
            ((pin->z) + (pin->velZ) < 0)) {
            pin->velZ = -(pin->velZ);
        }
        
        // floor contact flips and damps Z velocity
        if((pin->y) + (pin->velY) < floor) {
            pin->velX = (pin->velX) * 0.8f;
            pin->velY = -(pin->velY) * 0.10f;
            pin->velZ = (pin->velZ) * 0.8f;
            
            pin->y = pin->y + pin->velY;
            
            // fade out if too slow and near floor
            if(((pin->velY) < 0.6f) && (pin->y) < (floor + 2)) {
                pin->velX = 0;
                pin->velY = 0;
                pin->velZ = 0;
                //cin->r = (cin->r) - 0.01f;
                //cin->g = (cin->g) - 0.01f;
                //cin->b = (cin->b) - 0.01f;
                //cin->a = (cin->a) - 0.0025f;
            }
        } 
        
        if  (((pin->y) + (pin->velY)) > ceiling) {
            pin->velX = (pin->velX);
            pin->velY = -(pin->velY);
            pin->velZ = (pin->velZ);
        }
        
        // straight movement
        pin->x = (pin->x) + pin->velX;
        pin->y = (pin->y) + pin->velY;
        pin->z = (pin->z) + pin->velZ;
        
        // velocity change
        pin->velY = pin->velY + diffSpeedY;
        
    //###########################################################
    // WEIGHTLESS MODE

    } else if (mode == MODE_WEIGHTLESS) {
        if(pin->dir == 1) {
            pin->dir = 0;
        }
        // rightWall contact || leftWall contact
        if (((pin->x) + (pin->velX) > rightWall) ||
            ((pin->x) + (pin->velX) < leftWall)) {
            pin->velX = -(pin->velX);
        }
    
        // backWall contact || frontWall contact
        if (((pin->z) + (pin->velZ) > backWall) ||
            ((pin->z) + (pin->velZ) < 0)) {
            pin->velZ = -(pin->velZ);
        }

        // floor contact
        if (((pin->y) + (pin->velY) < floor) || 
            (((pin->y) + (pin->velY)) > ceiling)) {
            pin->velY = -(pin->velY) * 0.10f;
        }
        
        // velocity change
        pin->velX = pin->velX * 1.05f;
        pin->velY = pin->velY * 1.05f;
        pin->velZ = pin->velZ * 1.05f;
        
        // straight movement
        pin->x = (pin->x) + pin->velX;
        pin->y = (pin->y) + pin->velY;
        pin->z = (pin->z) + pin->velZ;

    //###########################################################
    // REVERSE WEIGHTLESS MODE

    } else if (mode == MODE_NEG_WEIGHTLESS) {
        if(pin->dir == 0){
            pin->velX = -pin->velX;
            pin->velY = -pin->velY;
            pin->velZ = -pin->velZ;
            pin->dir = 1;
        }
        
        // rightWall contact || leftWall contact
        if (((pin->x) + (pin->velX) > rightWall) ||
            ((pin->x) + (pin->velX) < leftWall)) {
            pin->velX = -(pin->velX);
        }
    
        // backWall contact || frontWall contact
        if (((pin->z) + (pin->velZ) > backWall) ||
            ((pin->z) + (pin->velZ) < 0)) {
            pin->velZ = -(pin->velZ);
        }

        // floor contact
        if (((pin->y) + (pin->velY) < floor) || 
            (((pin->y) + (pin->velY)) > ceiling)) {
            pin->velY = -(pin->velY) * 0.10f;
        }
        
        // velocity change
//        pin->velX = pin->velX * 1.01f;
//        pin->velY = pin->velY * 1.01f;
//        pin->velZ = pin->velZ * 1.01f;
        pin->velX = pin->velX / 1.05f;
        pin->velY = pin->velY / 1.05f;
        pin->velZ = pin->velZ / 1.05f;
        
        // straight movement
        pin->x = (pin->x) + pin->velX;
        pin->y = (pin->y) + pin->velY;
        pin->z = (pin->z) + pin->velZ;

    //###########################################################
    // RANDOM MODE

    } else if(mode == MODE_RANDOM) {
        if(pin->dir == 1) {
            pin->dir = 0;
        }

        // rightWall contact || leftWall contact
        if (((pin->x) + (pin->velX) > rightWall) ||
            ((pin->x) + (pin->velX) < leftWall)) {
            pin->velX = -(pin->velX);
        }
    
        // backWall contact || frontWall contact
        if (((pin->z) + (pin->velZ) > backWall) ||
            ((pin->z) + (pin->velZ) < 0)) {
            pin->velZ = -(pin->velZ);
        }
        
        // floor contact
        if (((pin->y) + (pin->velY) < floor) || 
            (((pin->y) + (pin->velY)) > ceiling)) {
            pin->velY = -(pin->velY);
        }

        
        // straight movement
        pin->x = (pin->x) + pin->velX;
        pin->y = (pin->y) + pin->velY;
        pin->z = (pin->z) + pin->velZ; 
        
        // velocity change
        /*
        if(pin->velX < 10.0f){ pin->velX = pin->velX * 1.02f; }
        if(pin->velY < 10.0f){ pin->velY = pin->velX * 1.02f; }
        if(pin->velZ < 10.0f){ pin->velZ = pin->velX * 1.02f; }
        */

    //###########################################################
    // PLANETARY MODE

    } else if(mode == MODE_PLANETARY) {

        float dirVectX = 0;
        float dirVectY = 0;
        float dirVectZ = 0;
        float dirLen = 0;

        // pull to gravitation centers
        for(int i = 0; i < 10; i++){
            if(comIn[i].x != 0 && comIn[i].y != 0 && comIn[i].z != 0){
				float tmpX= (comIn[i].x - pin->x);
				float tmpY= (comIn[i].y - pin->y);
				float tmpZ= (comIn[i].z - pin->z);
				float tmpLen = sqrt(tmpX * tmpX + tmpY * tmpY + tmpZ *tmpZ);
				
				// * 1/r^2
				dirVectX += tmpX * (1 / (tmpLen * tmpLen));
				dirVectY += tmpY * (1 / (tmpLen * tmpLen));
				dirVectZ += tmpZ * (1 / (tmpLen * tmpLen));
            }
        }
        
        // normalize
        dirLen = sqrt(dirVectX * dirVectX + dirVectY * dirVectY + dirVectZ * dirVectZ);
        dirVectX = (dirVectX / dirLen);
        dirVectY = (dirVectY / dirLen);
        dirVectZ = (dirVectZ / dirLen);

        // velocity vector change
        pin->velX = pin->velX + dirVectX * forceFactor;
        pin->velY = pin->velY + dirVectY * forceFactor;
        pin->velZ = pin->velZ + dirVectZ * forceFactor;

        // rightWall contact || leftWall contact
        if (((pin->x) + (pin->velX) > rightWall) ||
            ((pin->x) + (pin->velX) < leftWall)) {
            pin->velX = -(pin->velX) * 0.5f;
        }

        // backWall contact || frontWall contact
        if (((pin->z) + (pin->velZ) > backWall) ||
            ((pin->z) + (pin->velZ) < 0)) {
            pin->velZ = -(pin->velZ) * 0.5f;
        }

        // floor contact
        if (((pin->y) + (pin->velY) < floor) ||
            (((pin->y) + (pin->velY)) > ceiling)) {
            pin->velY = -(pin->velY) * 0.5f;
        }

        // straight movement
        pin->x = (pin->x) + pin->velX;
        pin->y = (pin->y) + pin->velY;
        pin->z = (pin->z) + pin->velZ;
    }
    
    
    
    
}

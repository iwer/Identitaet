#define GRAVITY -0.981f
#define DELTATIME 2.0f

#define MODE_GRAVITY 1
#define MODE_WEIGHTLESS 2
#define MODE_NEG_WEIGHTLESS 3

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

__kernel void updateParticle(__global Particle3D* pIn, 
                             __global Color* cIn ,
                             float floor, 
                             float leftWall, 
                             float rightWall, 
                             float backWall, 
                             int mode) {
    // Global work-item ID value
    int id = get_global_id(0);
    // Particle at index id
    __global Particle3D *pin = &pIn[id];
    __global Color *cin = &cIn[id];
    
    float diffSpeedY = GRAVITY * DELTATIME;

    
    if(mode == MODE_GRAVITY) {
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
        if((pin->y) + (pin->velY) < floor) {
            pin->velX = (pin->velX) * 0.8f;
            pin->velY = -(pin->velY) * 0.10f;
            pin->velZ = (pin->velZ) * 0.8f;
            
            pin->y = pin->y + pin->velY;
            
            if(((pin->velY) < 0.6f) && (pin->y) < (floor + 2)) {
                //pin->velX = 0;
                //pin->velY = 0;
                //pin->velZ = 0;
                //cin->r = (cin->r) - 0.01f;
                //cin->g = (cin->g) - 0.01f;
                //cin->b = (cin->b) - 0.01f;
                cin->a = (cin->a) - 0.0025f;
            }
        } 
        
        // straight movement
        pin->x = (pin->x) + pin->velX;
        pin->y = (pin->y) + pin->velY;
        pin->z = (pin->z) + pin->velZ;
        
        // velocity change
        pin->velY = pin->velY + diffSpeedY;
        
    } else if (mode == MODE_WEIGHTLESS) {
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
        if((pin->y) + (pin->velY) < floor) {
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
        if((pin->y) + (pin->velY) < floor) {
            pin->velY = -(pin->velY) * 0.10f;
        }
        
        // velocity change
        pin->velX = pin->velX * 1.01f;
        pin->velY = pin->velY * 1.01f;
        pin->velZ = pin->velZ * 1.01f;
        
        // straight movement
        pin->x = (pin->x) + pin->velX;
        pin->y = (pin->y) + pin->velY;
        pin->z = (pin->z) + pin->velZ;        
    }
    
    
    
    
}

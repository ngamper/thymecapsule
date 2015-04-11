#ifndef FLASHBACK_COMPASS_H
#define FLASHBACK_COMPASS_H

#define ARROW_WIDTH 20
#define ARROW_HEIGHT 30
#define GRANULARITY 10    // update threshold in degrees
#define KEY_DATA 18       // communication key

typedef struct {
  float x, y;
} Location;

#endif
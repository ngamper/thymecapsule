#include <pebble.h>
#include <math.h>
#include "flashback_compass.h"

static Window *main_window;
static TextLayer *text_layer;
static BitmapLayer *bitmap_layer;
static Layer *arrow_layer;
static GPath *arrow;
static Location cur_loc = {0, 0}, goal_loc = {0, 0};
static int last_heading = 0, new_heading = 0, distance = 0;

static const GPathInfo ARROW_POINTS = {
  3,
  (GPoint[]) { {-(ARROW_WIDTH / 2), ARROW_HEIGHT / 2}, {(ARROW_WIDTH / 2), ARROW_HEIGHT / 2}, {0, -(ARROW_HEIGHT / 2)} }
};

static int32_t getDirection() {
  // if goal coordinates are (0,0), no goal within range
  if (goal_loc.x == 0 && goal_loc.y == 0)
    return 0;
  
  return new_heading;
}

static int getDistance() {
  return distance; //(int) (sqrt(pow(goal_loc.y - cur_loc.y, 2) + pow(goal_loc.x - cur_loc.x, 2)));
}

static void update_screen() {
  // rotate arrow on heading change
  int new_direction = getDirection();
  gpath_rotate_to(arrow, (new_direction + last_heading) * TRIG_MAX_ANGLE / 360);
  
  // DEBUG
  if (getDirection() == 44) {
    text_layer_set_text(text_layer, "This is some shit");
  }
  
  // print distance or nothing found
  if (new_direction == 0) {
    text_layer_set_text(text_layer, "Nothing found");
  } else {
    char buffer[7], number[5];
    snprintf(number, sizeof(number), "%d", getDistance());
    strcpy(buffer, number);
    //strcpy(buffer + strlen(number), "ft\0");
    //text_layer_set_text(text_layer, number);
  }
  
  // mark layers to be redrawn
  layer_mark_dirty(arrow_layer);
  layer_mark_dirty(bitmap_layer_get_layer(bitmap_layer));
  layer_mark_dirty(text_layer_get_layer(text_layer));
}

static void compass_handler(CompassHeadingData data) {
  last_heading = data.magnetic_heading;
  update_screen();
}

static void arrow_layer_update_callback(Layer *path, GContext *ctx) {
  // draw arrow
  graphics_context_set_fill_color(ctx, GColorBlack);
  gpath_draw_filled(ctx, arrow);
  gpath_draw_outline(ctx, arrow);
}

// copy of isdigit to resolve external type-conflict errors
static int my_isdigit(char c) {
  return (c >= '0' && c <= '9');
}

// copy of atof to resolve external type-conflict errors
static double my_atof(char *s) {
        double a = 0.0;
        int e = 0;
        int c;
        while ((c = *s++) != '\0' && my_isdigit(c)) {
                a = a*10.0 + (c - '0');
        }
        if (c == '.') {
                while ((c = *s++) != '\0' && my_isdigit(c)) {
                        a = a*10.0 + (c - '0');
                        e = e-1;
                }
        }
        if (c == 'e' || c == 'E') {
                int sign = 1;
                int i = 0;
                c = *s++;
                if (c == '+')
                        c = *s++;
                else if (c == '-') {
                        c = *s++;
                        sign = -1;
                }
                while (my_isdigit(c)) {
                        i = i*10 + (c - '0');
                        c = *s++;
                }
                e += i*sign;
        }
        while (e > 0) {
                a *= 10.0;
                e--;
        }
        while (e < 0) {
                a *= 0.1;
                e++;
        }
        return a;
}

// copy of atoi 
int my_atoi(char *str) {
    int res = 0;  // Initialize result
    int sign = 1;  // Initialize sign as positive
    int i = 0;  // Initialize index of first digit
     
    // If number is negative, then update sign
    if (str[0] == '-') {
        sign = -1;  
        i++;  // Also update index of first digit
    }
     
    // Iterate through all digits and update the result
    for (; str[i] != '\0'; ++i)
        res = res*10 + str[i] - '0';
   
    // Return result with sign
    return sign*res;
}

static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Message received!");
  
  // reset values
  cur_loc.x = 0;
  cur_loc.y = 0;
  goal_loc.x = 0;
  goal_loc.y = 0;
  
  // store incoming data
  Tuple *t = dict_read_first(iterator);
  while(t != NULL) {
    switch (t->key) {
      case CUR_X: 
        cur_loc.x = my_atof(t->value->cstring);
        APP_LOG(APP_LOG_LEVEL_INFO, "CUR_X received!");
        break;
      case CUR_Y:
        cur_loc.y = my_atof(t->value->cstring);
        APP_LOG(APP_LOG_LEVEL_INFO, "CUR_Y received!");
        break;
      case END_X:
        goal_loc.x = my_atof(t->value->cstring);
        APP_LOG(APP_LOG_LEVEL_INFO, "GOAL_X received!");
        break;
      case END_Y:
        goal_loc.y = my_atof(t->value->cstring);
        APP_LOG(APP_LOG_LEVEL_INFO, "GOAL_Y received!");
        break;
      case DIR:
        new_heading = my_atoi(t->value->cstring);
        APP_LOG(APP_LOG_LEVEL_INFO, "DIR received!");
        break;
      case DIST:
        distance = my_atoi(t->value->cstring);
        APP_LOG(APP_LOG_LEVEL_INFO, "DIST received!");
        break;
      default:
        APP_LOG(APP_LOG_LEVEL_INFO, "Invalid key received");
        break;
    }
    t = dict_read_next(iterator);
  }
  update_screen();
}

static void inbox_dropped_callback(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Message dropped!");
}

static void outbox_failed_callback(DictionaryIterator *iterator, AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Outbox send failed!");
}

static void outbox_sent_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Outbox send success!");
}

static void main_window_load(Window *window) {
  // create TextLayer
  Layer *window_layer = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(window_layer);
  text_layer = text_layer_create(GRect(0, 130, bounds.size.w, bounds.size.h));
  if (text_layer == NULL) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "Text layer initialization failure");
  }
  text_layer_set_text(text_layer, "Nothing found");
  text_layer_set_text_alignment(text_layer, GTextAlignmentCenter);
  
  // create BitmapLayer
  bitmap_layer = bitmap_layer_create(GRect(0, 0, bounds.size.w, 130));
  
  // create arrow layer
  arrow_layer = layer_create(bounds);
  layer_set_update_proc(arrow_layer, arrow_layer_update_callback);
  
  // create and place arrow image
  arrow = gpath_create(&ARROW_POINTS);
  GPoint fixed_center = GPoint(bounds.size.w / 2, bounds.size.h / 2 - 10);
  gpath_move_to(arrow, fixed_center);
  
  // add as children
  layer_add_child(window_layer, text_layer_get_layer(text_layer));
  layer_add_child(window_layer, bitmap_layer_get_layer(bitmap_layer));
  layer_add_child(window_layer, arrow_layer);
}

static void main_window_unload(Window *window) {
	// destroy main_window children
  text_layer_destroy(text_layer);
  bitmap_layer_destroy(bitmap_layer);
}

void init(void) {
  // register compass service
  compass_service_subscribe(&compass_handler);
  compass_service_set_heading_filter(GRANULARITY);
  
  // prepare app communication service
  app_message_register_inbox_received(inbox_received_callback);
  app_message_register_inbox_dropped(inbox_dropped_callback);
  app_message_register_outbox_failed(outbox_failed_callback);
  app_message_register_outbox_sent(outbox_sent_callback);
  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());

  // create main window and set handlers
	main_window = window_create();
  if (main_window == NULL) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "Main window initialization failure");
  }
	window_set_window_handlers(main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload,
  });
  window_stack_push(main_window, true);
}

void deinit(void) {
  window_destroy(main_window);
}

int main(void) {
	init();
	app_event_loop();
	deinit();
	return 0;
}
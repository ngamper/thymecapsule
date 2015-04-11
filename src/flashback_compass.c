#include <pebble.h>

#define ARROW_WIDTH 20
#define ARROW_HEIGHT 30
#define GRANULARITY 10    // update threshold in degrees
  
static void main_window_load(Window *);
static void main_window_unload(Window *);

static Window *main_window;
static TextLayer *text_layer;
static BitmapLayer *bitmap_layer;
static Layer *arrow_layer;
static GPath *arrow;

static const GPathInfo ARROW_POINTS = {
  3,
  (GPoint[]) { {0, ARROW_HEIGHT}, {ARROW_WIDTH, ARROW_HEIGHT}, {ARROW_WIDTH / 2, 0} }
};

static void compass_handler(CompassHeadingData data) {
  gpath_rotate_to(arrow, data.magnetic_heading);
}

static void arrow_layer_update_callback(Layer *path, GContext *ctx) {
  // draw arrow
  graphics_context_set_fill_color(ctx, GColorBlack);
  gpath_draw_filled(ctx, arrow);
  gpath_draw_outline(ctx, arrow);
}

void init(void) {
  compass_service_subscribe(&compass_handler);
  compass_service_set_heading_filter(GRANULARITY);
  
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
  GPoint fixed_center = GPoint((bounds.size.w - ARROW_WIDTH) / 2, (bounds.size.h - ARROW_HEIGHT) / 2 - 10);
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

int main(void) {
	init();
	app_event_loop();
	deinit();
	return 0;
}
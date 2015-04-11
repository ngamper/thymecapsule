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
  (GPoint[]) { {-(ARROW_WIDTH / 2), ARROW_HEIGHT / 2}, {(ARROW_WIDTH / 2), ARROW_HEIGHT / 2}, {0, -(ARROW_HEIGHT / 2)} }
};

static void compass_handler(CompassHeadingData data) {
  // rotate arrow
  gpath_rotate_to(arrow, data.magnetic_heading);
  layer_mark_dirty(arrow_layer);
}

static void arrow_layer_update_callback(Layer *path, GContext *ctx) {
  // draw arrow
  graphics_context_set_fill_color(ctx, GColorBlack);
  gpath_draw_filled(ctx, arrow);
  gpath_draw_outline(ctx, arrow);
}

static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Message received!");
  // TODO: PROCESS MESSAGES
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

void init(void) {
  // register compass service
  compass_service_subscribe(&compass_handler);
  compass_service_set_heading_filter(GRANULARITY);
  
  // register app communication service
  app_message_register_inbox_received(inbox_received_callback);
  app_message_register_inbox_dropped(inbox_dropped_callback);
  app_message_register_outbox_failed(outbox_failed_callback);
  app_message_register_outbox_sent(outbox_sent_callback);
  
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

int main(void) {
	init();
	app_event_loop();
	deinit();
	return 0;
}
#include <pebble.h>
#include <stdlib.h>
  
static void main_window_load(Window *);
static void main_window_unload(Window *);

static Window *main_window;
static TextLayer *text_layer;
	
void init(void) {
  // create main window and set handlers
	main_window = window_create();
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
  text_layer = text_layer_create(GRect(10, 130, bounds.size.w, bounds.size.h));
  if (text_layer == NULL) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "Text layer initialization failure");
  }
  text_layer_set_text(text_layer, "Nothing found");
  
  // add as child
  layer_add_child(window_layer, text_layer_get_layer(text_layer));
}

static void main_window_unload(Window *window) {
	// destroy main_window children
  text_layer_destroy(text_layer);
}

int main(void) {
	init();
	app_event_loop();
	deinit();
	return 0;
}
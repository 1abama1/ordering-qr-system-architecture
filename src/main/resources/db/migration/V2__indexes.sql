CREATE INDEX idx_orders_status_created ON orders(status, created_at, id);
CREATE INDEX idx_orders_table ON orders(table_id);
CREATE INDEX idx_order_items_menu ON order_items(menu_item_id);
CREATE INDEX idx_menu_category ON menu_items(category_id);
CREATE INDEX idx_menu_public ON menu_items(category_id, id) WHERE is_active;

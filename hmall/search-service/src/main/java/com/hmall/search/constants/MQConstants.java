package com.hmall.search.constants;

public interface MQConstants {
    String CREATE_ITEM_QUEUE = "save_item_queue";
    String UPDATE_ITEM_QUEUE = "update_item_queue";
    String DELETE_ITEM_QUEUE = "delete_item_queue";
    String ITEM_EXCHANGE = "item_exchange";
    String CREATE_ITEM_ROUTING_KEY = "item.save";
    String UPDATE_ITEM_ROUTING_KEY = "item.update";
    String DELETE_ITEM_ROUTING_KEY = "item.delete";
}